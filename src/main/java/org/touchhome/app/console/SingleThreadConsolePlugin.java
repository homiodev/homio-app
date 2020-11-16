package org.touchhome.app.console;

import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextBGPImpl;

@Component
public class SingleThreadConsolePlugin extends BaseProcessesConsolePlugin implements NamedConsolePlugin {

    public SingleThreadConsolePlugin(EntityContextImpl entityContextImpl) {
        super(entityContextImpl);
    }

    @Override
    protected boolean matchProcess(EntityContextBGPImpl.ThreadContextImpl threadContext) {
        return threadContext.getPeriod() == null;
    }

    @Override
    public String getName() {
        return "thread";
    }
}
