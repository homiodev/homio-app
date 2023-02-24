package org.touchhome.app.model.var;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.touchhome.bundle.api.inmemory.InMemoryDBEntity;

@NoArgsConstructor
public class WorkspaceVariableMessage extends InMemoryDBEntity {

    @Getter @Setter private Object value;

    public WorkspaceVariableMessage(Object value) {
        this.value = value;
    }

    public static WorkspaceVariableMessage of(VariableBackup data, Object value) {
        WorkspaceVariableMessage message = new WorkspaceVariableMessage(value);
        message.setId(data.getId());
        message.setCreated(data.getCreated());
        return message;
    }
}
