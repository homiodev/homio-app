package org.homio.app.console;

import static org.homio.app.model.entity.user.UserBaseEntity.SSH_RESOURCE;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SshConsolePlugin implements ConsolePlugin<Object> {

    @Getter
    private final @Accessors(fluent = true) Context context;

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public @NotNull RenderType getRenderType() {
        return RenderType.string;
    }

    @Override
    public @NotNull String getName() {
        return "ssh";
    }

    @Override
    public boolean isEnabled() {
        return context.accessEnabled(SSH_RESOURCE);
    }

    @Override
    public boolean hasRefreshIntervalSetting() {
        return false;
    }
}
