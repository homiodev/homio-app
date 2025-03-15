package org.homio.app.manager.common.impl;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.homio.api.ContextUser;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.user.UserAdminEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Log4j2
public class ContextUserImpl implements ContextUser {

  @Getter
  private final @Accessors(fluent = true) ContextImpl context;

  public ContextUserImpl(ContextImpl context) {
    this.context = context;
  }

  @Override
  public boolean isRequireAuth() {
    UserAdminEntity user = context.db().getRequire(UserAdminEntity.class, PRIMARY_DEVICE);
    if (!user.getEmail().isEmpty()) {
      return !user.getPassword().isEmpty();
    }
    return false;
  }

  @SneakyThrows
  @Override
  public void assertUserCredentials(String username, String password) {
    UserAdminEntity user = context.db().getRequire(UserAdminEntity.class, PRIMARY_DEVICE);
    if (user.getEmail().equals(username)) {
      if (user.matchPassword(password, context.getBean(PasswordEncoder.class))) {
        return;
      }
    }
    throw new IllegalAccessException("User not match");
  }
}
