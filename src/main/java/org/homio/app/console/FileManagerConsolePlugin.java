package org.homio.app.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.console.ConsolePlugin;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileManagerConsolePlugin implements ConsolePlugin<Object> {

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
  public boolean isEnabled() {
    return entityContext.isAdminUserOrNone();
  }

  @Override
  public String getName() {
    return "fm";
  }
}
