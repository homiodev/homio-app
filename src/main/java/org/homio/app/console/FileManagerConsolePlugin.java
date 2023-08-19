package org.homio.app.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.springframework.stereotype.Component;

import static org.homio.app.model.entity.user.UserBaseEntity.FILE_MANAGER_RESOURCE;

@Component
@RequiredArgsConstructor
public class FileManagerConsolePlugin implements ConsolePlugin<Object> {

    @Getter
    private final EntityContext entityContext;

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public RenderType getRenderType() {
        return null;
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
