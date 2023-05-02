package org.homio.app.auth;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.homio.app.model.entity.user.UserBaseEntity;
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
                                         expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<>() {
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
            throw new BadCredentialsException("W.ERROR.USER_NOT_EXISTS_OR_WRONG_PASSWORD");
        }

        if (isBlocked(userDetails.getUsername())) {
            UserBaseEntity.logInfo(userDetails.getUsername(), "user blocked");
            throw new BadCredentialsException("W.ERROR.USER_BLOCKED");
        }

        String password = (String) authentication.getCredentials();

        try {
            checkPassword(userDetails, password);
        } catch (Exception ex) {
            UserBaseEntity.logInfo(userDetails.getUsername(), "wrong password");
            attemptsCache.put(userDetails.getUsername(), getAttempts(userDetails.getUsername()));
            throw ex;
        }

        UserBaseEntity.logInfo(userDetails.getUsername(), "auth success");
        attemptsCache.invalidate(userDetails.getUsername());
    }

    private void checkPassword(UserDetails userDetails, String presentedPassword) {
        if (getPasswordEncoder().matches(presentedPassword, userDetails.getPassword())
            || presentedPassword.equals(userDetails.getPassword())) {
            return;
        }
        throw new BadCredentialsException("W.ERROR.USER_NOT_EXISTS_OR_WRONG_PASSWORD");
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
