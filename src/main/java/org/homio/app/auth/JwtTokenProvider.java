package org.homio.app.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.setting.system.auth.SystemDisableAuthTokenOnRestartSetting;
import org.homio.app.setting.system.auth.SystemJWTTokenValidSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.bundle.api.util.CommonUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements ContextCreated {

    private final UserEntityDetailsService userEntityDetailsService;

    private JwtParser jwtParser;
    private boolean regenerateSecurityIdOnRestart;
    private int jwtValidityTimeout;
    private byte[] securityId;

    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        entityContext.setting().listenValueAndGet(SystemJWTTokenValidSetting.class, "jwt-valid", value -> {
            this.jwtValidityTimeout = value;
            this.securityId = buildSecurityId();
            this.jwtParser = Jwts.parser().setSigningKey(securityId);
        });
        entityContext.setting().listenValueAndGet(SystemDisableAuthTokenOnRestartSetting.class, "jwt-req-app", value -> {
            this.regenerateSecurityIdOnRestart = value;
            this.securityId = buildSecurityId();
            this.jwtParser = Jwts.parser().setSigningKey(securityId);
        });
    }

    String createToken(String username, Collection<? extends GrantedAuthority> roles) {

        Claims claims = Jwts.claims().setSubject(username);
        claims.put("auth", roles);

        Date now = new Date();
        Date validity = new Date(now.getTime() + TimeUnit.MINUTES.toMillis(jwtValidityTimeout));

        return Jwts.builder()
                   .setClaims(claims)
                   .setIssuedAt(now)
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
        return this.jwtParser.parseClaimsJws(token).getBody().getSubject();
    }

    private byte[] buildSecurityId() {
        String securityId = CommonUtils.APP_UUID + "_" + jwtValidityTimeout;
        if (regenerateSecurityIdOnRestart) {
            securityId += "_" + CommonUtils.RUN_COUNT;
        }
        return Base64.getEncoder().encode(securityId.getBytes());
    }
}
