package org.touchhome.app.console;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;

@Component
@RequiredArgsConstructor
public class FileManagerConsolePlugin implements ConsolePlugin<Object> {

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
        return entityContext.isAdminUserOrNone();
    }

    @Override
    public String getName() {
        return "fm";
    }
}
