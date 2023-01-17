package org.touchhome.app.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.touchhome.app.repository.UserRepository;
import org.touchhome.bundle.api.entity.UserEntity;

@Service
public class UserEntityDetailsService implements UserDetailsService {

  @Autowired
  private UserRepository userRepository;

  private UserEntity user;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    if (this.user == null) {
      this.user = userRepository.getUser(email);
      if (user == null) {
        throw new UsernameNotFoundException("User with email: " + email + " not found");
      }
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
