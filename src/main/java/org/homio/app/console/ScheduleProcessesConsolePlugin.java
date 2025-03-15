package org.homio.app.console;

import org.homio.app.manager.common.ContextImpl;
import org.springframework.stereotype.Component;

@Component
public class ScheduleProcessesConsolePlugin extends BaseProcessesConsolePlugin {

  public ScheduleProcessesConsolePlugin(ContextImpl contextImpl) {
    super(contextImpl);
  }

  @Override
  public String getName() {
    return "schedule";
  }

  @Override
  protected boolean handleThreads() {
    return false;
  }
}
