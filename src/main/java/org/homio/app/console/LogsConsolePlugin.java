package org.homio.app.console;

import static org.homio.app.model.entity.user.UserBaseEntity.LOG_RESOURCE;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.console.ConsolePluginLines;
import org.homio.app.LogService;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class LogsConsolePlugin implements ConsolePluginLines {

    @Getter
    private final @Accessors(fluent = true) Context context;
    private final LogService logService;
    private final String name;

    @Override
    public List<String> getValue() {
        context.assertAccess(LOG_RESOURCE);
        return this.logService.getLogs(name);
    }

    @Override
    public ConsolePlugin.@NotNull RenderType getRenderType() {
        return RenderType.lines;
    }

    @Override
    public boolean isEnabled() {
        return context.accessEnabled(LOG_RESOURCE);
    }

    @Override
    public @NotNull String getName() {
        return "logs";
    }
}
