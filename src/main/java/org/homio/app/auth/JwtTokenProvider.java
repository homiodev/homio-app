package org.homio.app.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.setting.system.SystemLogoutButtonSetting;
import org.homio.app.setting.system.auth.SystemDisableAuthTokenOnRestartSetting;
import org.homio.app.setting.system.auth.SystemJWTTokenValidSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.bundle.api.util.CommonUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements ContextCreated {

    private final UserEntityDetailsService userEntityDetailsService;
    private final AtomicInteger LOGOUT_INCREMENTER = new AtomicInteger(0);

    private JwtParser jwtParser;
    private boolean regenerateSecurityIdOnRestart;
    private int jwtValidityTimeout;
    private byte[] securityId;

    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        entityContext.setting().listenValueAndGet(SystemJWTTokenValidSetting.class, "jwt-valid", value -> {
            this.jwtValidityTimeout = value;
            regenerateSecurityID();
            log.info("Generated securityID: {}. Valid timeout: {}", securityId, value);
        });
        entityContext.setting().listenValueAndGet(SystemDisableAuthTokenOnRestartSetting.class, "jwt-req-app", value -> {
            this.regenerateSecurityIdOnRestart = value;
            regenerateSecurityID();
            log.info("Generated securityID: {}. Regenerate security id on restart: {}", securityId, value);
        });
        entityContext.setting().listenValue(SystemLogoutButtonSetting.class, "logout", () -> {
            LOGOUT_INCREMENTER.incrementAndGet();
            regenerateSecurityID();
            log.info("Generated securityID: {}. Regenerate security id on logout: {}", securityId, entityContext.getUser());
        });
    }

    private void regenerateSecurityID() {
        this.securityId = buildSecurityId();
        this.jwtParser = Jwts.parser().setSigningKey(securityId);
    }

    String createToken(String username, Collection<? extends GrantedAuthority> roles) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + TimeUnit.MINUTES.toMillis(jwtValidityTimeout));

        return Jwts.builder()
                   .setId(UUID.randomUUID().toString())
                   .setAudience(username)
                   .claim("auth", roles)
                   .setIssuedAt(now)
                   .setIssuer("homio_app")
                   .setExpiration(validity)
                   .signWith(SignatureAlgorithm.HS256, securityId)
                   .compact();
    }

    public Authentication getAuthentication(String token) {
        UserDetails userDetails = userEntityDetailsService.loadUserByUsername(getUsername(token));
        return new UsernamePasswordAuthenticationToken(userDetails, userDetails.getUsername(), userDetails.getAuthorities());
    }

    public String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public boolean validateToken(String token) {
        // in case if postConstruct not yet fired but ui send request to backend
        if (this.jwtParser == null) {
            return false;
        }
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
        String securityId = CommonUtils.APP_UUID + "_" + jwtValidityTimeout + "_" + LOGOUT_INCREMENTER.get();
        if (regenerateSecurityIdOnRestart) {
            securityId += "_" + CommonUtils.RUN_COUNT;
        }
        return Base64.getEncoder().encode(securityId.getBytes());
    }
}
