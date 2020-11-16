package org.touchhome.app.auth;

import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.touchhome.app.setting.system.SystemDisableAuthTokenOnRestartSetting;
import org.touchhome.app.setting.system.SystemJWTTokenValidSetting;
import org.touchhome.bundle.api.EntityContext;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String STATIC_KEY = "poorSecurity";
    private final UserEntityDetailsService userEntityDetailsService;
    @Value("${security.jwt.token.expire-length:1800000}")
    private long jwtValidityTimeout; // 30min


    private JwtParser jwtParser;
    private boolean requireAppID;

    public void postConstruct(EntityContext entityContext) {
        this.requireAppID = entityContext.setting().getValue(SystemDisableAuthTokenOnRestartSetting.class);
        createJWTParser();
        this.jwtValidityTimeout = entityContext.setting().getValue(SystemJWTTokenValidSetting.class);
        entityContext.setting().listenValue(SystemJWTTokenValidSetting.class, "jwt-valid", value -> this.jwtValidityTimeout = value);
        entityContext.setting().listenValue(SystemDisableAuthTokenOnRestartSetting.class, "jwt-req-app", value -> {
            this.requireAppID = value;
            createJWTParser();
        });
    }

    String createToken(String username, Set<String> roles) {

        Claims claims = Jwts.claims().setSubject(username);
        claims.put("auth", roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));

        Date now = new Date();
        Date validity = new Date(now.getTime() + TimeUnit.MINUTES.toMillis(jwtValidityTimeout));

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(SignatureAlgorithm.HS256, requireAppID ? EntityContext.APP_ID : STATIC_KEY)
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

    private void createJWTParser() {
        this.jwtParser = Jwts.parser().setSigningKey(requireAppID ? EntityContext.APP_ID : STATIC_KEY);
    }
}
