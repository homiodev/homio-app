package org.homio.app.console;

import org.homio.app.manager.common.EntityContextImpl;
import org.springframework.stereotype.Component;

@Component
public class ScheduleProcessesConsolePlugin extends BaseProcessesConsolePlugin {

    public ScheduleProcessesConsolePlugin(EntityContextImpl entityContextImpl) {
        super(entityContextImpl);
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
