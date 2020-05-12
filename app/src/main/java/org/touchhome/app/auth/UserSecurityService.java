package org.touchhome.app.auth;

import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;

@Service
@AllArgsConstructor
public class UserSecurityService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * For now we may have only one regular user
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.getUser(email);
        if (user == null) {
            throw new UsernameNotFoundException("User with email: " + email + " not found");
        }
        return user.createUserDetails();
    }
}
