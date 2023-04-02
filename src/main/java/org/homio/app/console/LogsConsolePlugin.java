package org.homio.app.console;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.app.LogService;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.console.ConsolePlugin;
import org.homio.bundle.api.console.ConsolePluginLines;

@RequiredArgsConstructor
public class LogsConsolePlugin implements ConsolePluginLines {

  @Getter
  private final EntityContext entityContext;
  private final LogService logService;
  private final String name;

  @Override
  public List<String> getValue() {
    return this.logService.getLogs(name);
  }

  @Override
  public ConsolePlugin.RenderType getRenderType() {
    return RenderType.lines;
  }

  @Override
  public String getName() {
    return "logs";
  }
}
