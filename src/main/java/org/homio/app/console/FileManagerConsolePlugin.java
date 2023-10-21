package org.homio.app.console;

import static org.homio.app.model.entity.user.UserBaseEntity.FILE_MANAGER_RESOURCE;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Getter
@Component
@RequiredArgsConstructor
public class FileManagerConsolePlugin implements ConsolePlugin<Object> {

    private final @Accessors(fluent = true) Context context;

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public @NotNull RenderType getRenderType() {
        return RenderType.tree;
    }

    @Override
    public boolean isEnabled() {
        return context.accessEnabled(FILE_MANAGER_RESOURCE);
    }

    @Override
    public String getName() {
        return "fm";
    }
}
