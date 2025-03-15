package org.homio.app.console;

import org.homio.app.manager.common.ContextImpl;
import org.springframework.stereotype.Component;

@Component
public class SingleThreadConsolePlugin extends BaseProcessesConsolePlugin {

  public SingleThreadConsolePlugin(ContextImpl contextImpl) {
    super(contextImpl);
  }

  @Override
  public String getName() {
    return "thread";
  }

  @Override
  protected boolean handleThreads() {
    return true;
  }
}
