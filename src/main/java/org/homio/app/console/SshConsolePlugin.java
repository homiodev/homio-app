package org.homio.app.console;

import static org.homio.app.model.entity.user.UserBaseEntity.SSH_RESOURCE;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.console.ConsolePlugin;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SshConsolePlugin implements ConsolePlugin<Object> {

  @Getter
  private final EntityContext entityContext;

  @Override
  public Object getValue() {
    return null;
  }

  @Override
  public RenderType getRenderType() {
    return null;
  }

  @Override
  public String getName() {
    return "ssh";
  }

  @Override
  public boolean isEnabled() {
    return entityContext.accessEnabled(SSH_RESOURCE);
  }
}
