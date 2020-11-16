package org.touchhome.app.console;

import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextBGPImpl;

@Component
public class ScheduleProcessesConsolePlugin extends BaseProcessesConsolePlugin implements NamedConsolePlugin {

    public ScheduleProcessesConsolePlugin(EntityContextImpl entityContextImpl) {
        super(entityContextImpl);
    }

    @Override
    public boolean matchProcess(EntityContextBGPImpl.ThreadContextImpl<?> threadContext) {
        return threadContext.getPeriod() != null;
    }

    @Override
    public String getName() {
        return "schedule";
    }
}
