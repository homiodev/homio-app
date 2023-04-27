package org.homio.app.auth;

import lombok.RequiredArgsConstructor;
import org.homio.app.model.entity.UserEntityImpl;
import org.homio.app.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserEntityDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String name) throws UsernameNotFoundException {
        UserEntityImpl user = userRepository.getUser(name);
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
