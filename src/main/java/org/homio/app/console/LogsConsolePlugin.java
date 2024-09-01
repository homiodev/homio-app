package org.homio.app.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.console.ConsolePluginLines;
import org.homio.app.LogService;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@RequiredArgsConstructor
public class LogsConsolePlugin implements ConsolePluginLines {

    @Getter
    private final @Accessors(fluent = true) Context context;
    private final LogService logService;
    private final String name;

    @Override
    public List<String> getValue() {
        try {
            UserGuestEntity.assertLogAccess(context);
            return this.logService.getLogs(name);
        } catch (Exception e) {
            return List.of(e.getMessage());
        }
    }

    @Override
    public ConsolePlugin.@NotNull RenderType getRenderType() {
        return RenderType.lines;
    }

    @Override
    public boolean isEnabled() {
        UserGuestEntity.assertLogAccess(context);
        return true;
    }

    @Override
    public @NotNull String getName() {
        return "logs";
    }
}
