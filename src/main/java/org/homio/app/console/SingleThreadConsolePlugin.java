package org.homio.app.console;

import org.homio.app.manager.common.EntityContextImpl;
import org.springframework.stereotype.Component;

@Component
public class SingleThreadConsolePlugin extends BaseProcessesConsolePlugin {

    public SingleThreadConsolePlugin(EntityContextImpl entityContextImpl) {
        super(entityContextImpl);
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
