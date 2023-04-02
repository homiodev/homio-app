package org.homio.app.auth;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

public class CacheAuthenticationProvider extends DaoAuthenticationProvider {

    private static final int MAX_ATTEMPT = 10;

    private final LoadingCache<String, Integer> attemptsCache;

    public CacheAuthenticationProvider() {
        this.attemptsCache = CacheBuilder.newBuilder().
                                         expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<String, Integer>() {
                public Integer load(@SuppressWarnings("NullableProblems") String ignore) {
                    return 0;
                }
            });
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication)
        throws AuthenticationException {
        if (authentication.getCredentials() == null) {
            logger.debug("Authentication failed: no credentials provided");

            throw new BadCredentialsException(
                messages.getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
        }

        if (isBlocked(userDetails.getUsername())) {
            throw new BadCredentialsException("USER.BLOCKED");
        }

        String password = (String) authentication.getCredentials();

        try {
            checkPassword(userDetails, password);
        } catch (Exception ex) {
            attemptsCache.put(userDetails.getUsername(), getAttempts(userDetails.getUsername()));
            throw ex;
        }

        attemptsCache.invalidate(userDetails.getUsername());
    }

    private void checkPassword(UserDetails userDetails, String presentedPassword) {
        if (getPasswordEncoder().matches(presentedPassword, userDetails.getPassword())
            || presentedPassword.equals(userDetails.getPassword())) {
            return;
        }
        throw new BadCredentialsException(messages.getMessage(
            "AbstractUserDetailsAuthenticationProvider.badCredentials",
            "Bad credentials"));
    }

    private boolean isBlocked(String key) {
        try {
            return attemptsCache.get(key) >= MAX_ATTEMPT;
        } catch (ExecutionException e) {
            return false;
        }
    }

    private int getAttempts(String key) {
        try {
            return attemptsCache.get(key) + 1;
        } catch (ExecutionException e) {
            return 1;
        }
    }
}
