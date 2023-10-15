package org.homio.app.console;

import static org.homio.app.model.entity.user.UserBaseEntity.FILE_MANAGER_RESOURCE;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Getter
@Component
@RequiredArgsConstructor
public class FileManagerConsolePlugin implements ConsolePlugin<Object> {

    private final EntityContext entityContext;

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
        return entityContext.accessEnabled(FILE_MANAGER_RESOURCE);
    }

    @Override
    public String getName() {
        return "fm";
    }
}
