package org.homio.app.auth;

import static org.apache.commons.lang3.StringUtils.defaultString;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Log4j2
@RequiredArgsConstructor
public class AccessFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain)
            throws ServletException, IOException {
        log.debug("Request: {}. Host: {}", request.getRequestURI(), request.getHeader("Host"));
        if(request.getRequestURI().startsWith("/rest/route/proxy")) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getRequestURI().equals("/")) {
            response.setContentType("text/html");
            response.getOutputStream().write("""
                <html>
                <body>
                    Homio backend app. <div>Please, visit '<a href="https://homio.org">https://homio.org</a>' for usage</div>
                </body>
                </html>
                """.getBytes());
            return;
        }
        String token = jwtTokenProvider.resolveToken(defaultString(request.getHeader("Authorization"), request.getParameter("Authorization")));
        try {
            if (token != null) {
                if (jwtTokenProvider.validateToken(token)) {
                    try {
                        Authentication auth = jwtTokenProvider.getAuthentication(token);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } catch (Exception ex) {
                        jwtTokenProvider.revokeToken(token);
                    }
                } else {
                    jwtTokenProvider.revokeToken(token);
                }
            }
            /*String accessId = request.getParameter("access_id");
            if(accessId != null) {
                String userId = jwtTokenProvider.generateUserId(accessId, request.getHeader("password"));
                response.setContentType("text/plain");
                response.getOutputStream().write(userId.getBytes());
                return;
            } else {
                String guestToken = request.getParameter("guest_token");
                jwtTokenProvider.validateGuestUser(guestToken);
            }*/
            chain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(420, ex.getMessage());
        } catch (BadCredentialsException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(419, ex.getMessage());
        }
    }
}
