package org.touchhome.app.auth;

import lombok.AllArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;

/**
 * Checks with encoded password and raw
 */
@AllArgsConstructor
public class DoubleCheckPasswordAuthenticationProvider extends DaoAuthenticationProvider {

    private final UserRepository userRepository;

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
        if (authentication.getCredentials() == null) {
            logger.debug("Authentication failed: no credentials provided");

            throw new BadCredentialsException(messages.getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
        }

        Credentials credentials = (Credentials) authentication.getCredentials();

        try {
            checkPassword(userDetails, credentials.getPassword());
        } catch (BadCredentialsException ex) {
            if (StringUtils.isNotEmpty(credentials.getOp())) {
                checkPassword(userDetails, credentials.getOp());
                // time to change password
                UserEntity user = userRepository.getUser((String) authentication.getPrincipal());
                userRepository.save(user.setPassword(credentials.getOp()));
            }
        }
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
}
