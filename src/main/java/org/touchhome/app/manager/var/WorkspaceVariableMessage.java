package org.touchhome.app.manager.var;

import lombok.Getter;
import org.touchhome.bundle.api.inmemory.InMemoryDBEntity;

public class WorkspaceVariableMessage extends InMemoryDBEntity {
    @Getter
    private Object value;

    public WorkspaceVariableMessage(Object value) {
        this.value = value;
    }
}
