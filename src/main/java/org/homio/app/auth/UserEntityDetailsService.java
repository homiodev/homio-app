package org.homio.app.auth;

import lombok.RequiredArgsConstructor;
import org.homio.app.model.entity.user.UserBaseEntity;
import org.homio.app.repository.device.AllDeviceRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Set;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

@Service
@RequiredArgsConstructor
public class UserEntityDetailsService implements UserDetailsService {

  private final AllDeviceRepository deviceRepository;

  public static String getEmail(Authentication auth) {
    UserDetails details = (UserDetails) auth.getPrincipal();
    return details.getUsername().split(LIST_DELIMITER)[1];
  }

  public static String getEntityID(Authentication auth) {
    UserDetails details = (UserDetails) auth.getPrincipal();
    return details.getUsername().split(LIST_DELIMITER)[0];
  }

  @Override
  public UserDetails loadUserByUsername(String name) {
    UserBaseEntity user = deviceRepository.getByIeeeAddressOrName(name);
    if (user == null) {
      throw new IllegalStateException("W.ERROR.USER_NOT_EXISTS_OR_WRONG_PASSWORD");
    }
    Set<String> roles = user.getRoles();
    return User
      .withUsername(user.getEntityID() + LIST_DELIMITER + name)
      .password(user.getPassword().asString())
      .authorities(roles.toArray(new String[0]))
      .build();
  }
}
