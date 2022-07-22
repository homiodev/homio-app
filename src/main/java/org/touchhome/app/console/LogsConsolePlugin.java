package org.touchhome.app.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.LogService;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.console.ConsolePluginLines;

import java.util.List;

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
