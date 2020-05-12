package org.touchhome.app.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Checks with encoded password and raw
 */
public class DoubleCheckPasswordAuthenticationProvider extends DaoAuthenticationProvider {

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
        try {
            super.additionalAuthenticationChecks(userDetails, authentication);
        } catch (Exception ex) {
            if (authentication.getCredentials() == null) {
                logger.debug("Authentication failed: no credentials provided");

                throw new BadCredentialsException(messages.getMessage(
                        "AbstractUserDetailsAuthenticationProvider.badCredentials",
                        "Bad credentials"));
            }

            String presentedPassword = authentication.getCredentials().toString();

            if (!presentedPassword.equals(userDetails.getPassword())) {
                logger.debug("Authentication failed: password does not match stored value");

                throw new BadCredentialsException(messages.getMessage(
                        "AbstractUserDetailsAuthenticationProvider.badCredentials",
                        "Bad credentials"));
            }
        }
    }
}
