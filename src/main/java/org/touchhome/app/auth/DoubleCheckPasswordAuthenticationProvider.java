package org.touchhome.app.auth;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.UserRepository;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Checks with encoded password and raw
 */
public class DoubleCheckPasswordAuthenticationProvider extends DaoAuthenticationProvider {

    private static final int MAX_ATTEMPT = 10;
    private final UserRepository userRepository;

    private final LoadingCache<String, Integer> attemptsCache;

    public DoubleCheckPasswordAuthenticationProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.attemptsCache = CacheBuilder.newBuilder().
                expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<String, Integer>() {
            public Integer load(@SuppressWarnings("NullableProblems") String ignore) {
                return 0;
            }
        });
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
        if (authentication.getCredentials() == null) {
            logger.debug("Authentication failed: no credentials provided");

            throw new BadCredentialsException(messages.getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
        }

        if (isBlocked(userDetails.getUsername())) {
            throw new BadCredentialsException("USER.BLOCKED");
        }

        Credentials credentials = (Credentials) authentication.getCredentials();

        try {
            checkPassword(userDetails, credentials.getPassword());
        } catch (BadCredentialsException ex) {
            if (StringUtils.isNotEmpty(credentials.getOp())) {
                try {
                    checkPassword(userDetails, credentials.getOp());
                } catch (BadCredentialsException ex2) {
                    attemptsCache.put(userDetails.getUsername(), getAttempts(userDetails.getUsername()));
                    throw ex2;
                }
                // time to change password
                UserEntity user = userRepository.getUser((String) authentication.getPrincipal());
                userRepository.save(user.setPassword(credentials.getOp(), getPasswordEncoder()));
            }
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
