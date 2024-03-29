package org.homio.app.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.entity.UserEntity;
import org.homio.api.util.HardwareUtils;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.setting.system.SystemClearCacheButtonSetting;
import org.homio.app.setting.system.SystemLogoutButtonSetting;
import org.homio.app.setting.system.auth.SystemDisableAuthTokenOnRestartSetting;
import org.homio.app.setting.system.auth.SystemJWTTokenValidSetting;
import org.homio.app.spring.ContextCreated;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements ContextCreated {

    private final UserEntityDetailsService userEntityDetailsService;
    private final Object NULL = new Object();
    private final Map<String, Authentication> userCache = new ConcurrentHashMap<>();
    private final Map<String, Object> blockedTokens = new ConcurrentHashMap<>();
    private JwtParser jwtParser;
    private boolean regenerateSecurityIdOnRestart;
    private int jwtValidityTimeout;
    private byte[] securityId;

    @Override
    public void onContextCreated(ContextImpl context) throws Exception {
        context.setting().listenValue(SystemClearCacheButtonSetting.class, "jwt-clear-cache", () -> {
            userCache.clear();
            blockedTokens.clear();
        });

        this.jwtValidityTimeout = context.setting().getValue(SystemJWTTokenValidSetting.class);
        this.regenerateSecurityIdOnRestart = context.setting().getValue(SystemDisableAuthTokenOnRestartSetting.class);
        regenerateSecurityID(context);
        context.setting().listenValue(SystemJWTTokenValidSetting.class, "jwt-valid", value -> {
            this.jwtValidityTimeout = value;
            regenerateSecurityID(context);
            log.info("Generated securityID: {} on change timeout: {}", securityId, value);
        });
        context.setting().listenValue(SystemDisableAuthTokenOnRestartSetting.class, "jwt-req-app", value -> {
            this.regenerateSecurityIdOnRestart = value;
            regenerateSecurityID(context);
            log.info("Generated securityID: {} on disable auth on restart: {}", securityId, value);
        });
        context.setting().listenValue(SystemLogoutButtonSetting.class, "logout", () -> {
            UserEntity user = context.getUser();
            if (user != null) {
                boolean removed = userCache.entrySet().removeIf(entry -> {
                    if (user.getEntityID().equals(UserEntityDetailsService.getEntityID(entry.getValue()))) {
                        blockedTokens.put(entry.getKey(), NULL);
                        return true;
                    }
                    return false;
                });
                if (removed) {
                    user.logInfo("logged out");
                    context.ui().notification().removeBlock("user-" + user.getEmail());
                    context.ui().dialog().reloadWindow("sys.auth_changed");
                }
            }
        });
    }

    public void revokeToken(String token) {
        userCache.remove(token);
    }

    public Authentication getAuthentication(String token) {
        return userCache.computeIfAbsent(token, key -> {
            removeOutdatedTokens();

            String userName = getUsername(key);
            UserDetails userDetails = userEntityDetailsService.loadUserByUsername(userName);
            return new UsernamePasswordAuthenticationToken(userDetails,
                    userDetails.getUsername(), userDetails.getAuthorities());
        });
    }

    public boolean validateToken(String token) {
        // in case if postConstruct not yet fired but ui send request to backend
        if (this.jwtParser == null) {
            return false;
        }
        if (blockedTokens.containsKey(token)) {
            throw new ExpiredJwtException(null, null, "Token is expired");
        }
        return isTokenValid(token);
    }

    public String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    String createToken(String username, Authentication authentication) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + TimeUnit.MINUTES.toMillis(jwtValidityTimeout));

        String token = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setAudience(username)
                .claim("auth", authentication.getAuthorities())
                .setIssuedAt(now)
                .setIssuer("homio_app")
                .setExpiration(validity)
                .signWith(SignatureAlgorithm.HS256, securityId)
                .compact();
        userCache.put(token, authentication);
        removeOutdatedTokens();
        return token;
    }

    private void regenerateSecurityID(ContextImpl context) {
        this.securityId = buildSecurityId();
        this.jwtParser = Jwts.parser().setSigningKey(securityId).requireIssuer("homio_app");
        context.ui().dialog().reloadWindow("sys.auth_changed");
    }

    private void removeOutdatedTokens() {
        blockedTokens.keySet().removeIf(blockToken -> !isTokenValid(blockToken));
    }

    private boolean isTokenValid(String token) {
        try {
            this.jwtParser.parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private String getUsername(String token) {
        return this.jwtParser.parseClaimsJws(token).getBody().getAudience();
    }

    private byte[] buildSecurityId() {
        userCache.clear();
        String securityId = HardwareUtils.APP_ID + "_" + jwtValidityTimeout;
        if (regenerateSecurityIdOnRestart) {
            securityId += "_" + HardwareUtils.RUN_COUNT;
        }
        return Base64.getEncoder().encode(securityId.getBytes());
    }
}
