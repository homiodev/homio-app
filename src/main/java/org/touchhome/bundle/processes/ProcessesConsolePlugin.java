package org.touchhome.bundle.processes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.BackgroundProcessManager;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.HasEntityIdentifier;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProcessesConsolePlugin implements ConsolePlugin {

    private final BackgroundProcessManager backgroundProcessManager;

    @Override
    public List<? extends HasEntityIdentifier> drawEntity() {
        return this.backgroundProcessManager.getAllBackgroundProcesses();
    }

    @Override
    public int order() {
        return 1000;
    }
}
