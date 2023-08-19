package org.homio.app.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.console.ConsolePluginLines;
import org.homio.app.LogService;

import java.util.List;

import static org.homio.app.model.entity.user.UserBaseEntity.LOG_RESOURCE;

@RequiredArgsConstructor
public class LogsConsolePlugin implements ConsolePluginLines {

    @Getter
    private final EntityContext entityContext;
    private final LogService logService;
    private final String name;

    @Override
    public List<String> getValue() {
        entityContext.assertAccess(LOG_RESOURCE);
        return this.logService.getLogs(name);
    }

    @Override
    public ConsolePlugin.RenderType getRenderType() {
        return RenderType.lines;
    }

    @Override
    public boolean isEnabled() {
        return entityContext.accessEnabled(LOG_RESOURCE);
    }

    @Override
    public String getName() {
        return "logs";
    }
}
