package org.homio.app.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Getter
@Component
@RequiredArgsConstructor
public class FileManagerConsolePlugin implements ConsolePlugin<Object> {

    public static final String NAME = "fm";

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
        try {
            UserGuestEntity.assertFileManagerReadAccess(context);
        } catch (Exception ignore) {
            UserGuestEntity.assertFileManagerWriteAccess(context);
        }
        return true;
    }

    @Override
    public @NotNull String getName() {
        return NAME;
    }

    @Override
    public boolean hasRefreshIntervalSetting() {
        return false;
    }
}
