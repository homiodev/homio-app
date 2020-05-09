package org.touchhome.app.manager;

import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;

import java.util.Collections;

@Service
@AllArgsConstructor
public class UserSecurityService implements UserDetailsService {

    private final EntityContext entityContext;

    /**
     * For now we may have only one regular user
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = entityContext.getEntity(UserRepository.ADMIN_USER);
        if (user == null) {
            throw new UsernameNotFoundException("User with email: " + email + " not found");
        }
        return buildUserForAuthentication(user);
    }

    private UserDetails buildUserForAuthentication(UserEntity user) {
        return user.createUserDetails();
    }
}
