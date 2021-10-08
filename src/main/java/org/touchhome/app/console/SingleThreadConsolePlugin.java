package org.touchhome.app.console;

import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextImpl;

@Component
public class SingleThreadConsolePlugin extends BaseProcessesConsolePlugin implements NamedConsolePlugin {

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
