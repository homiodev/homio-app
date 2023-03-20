package org.touchhome.app.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.touchhome.app.repository.UserRepository;
import org.touchhome.bundle.api.entity.UserEntity;

@Service
@RequiredArgsConstructor
public class UserEntityDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String name) throws UsernameNotFoundException {
        UserEntity user = userRepository.getUser(name);
        if (user == null) {
            throw new UsernameNotFoundException("User: " + name + " not found");
        }
        return org.springframework.security.core.userdetails.User
            .withUsername(user.getEntityID())
            .password(user.getPassword())
            .authorities(user.getRoles().toArray(new String[0]))
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(false)
            .build();
    }
}
