package org.homio.app.model.var;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.homio.api.storage.DataStorageEntity;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceVariableMessage extends DataStorageEntity {

    // @Setter need for deserializing from mongo
    private Object value;

    public static WorkspaceVariableMessage of(VariableBackup data, Object value) {
        WorkspaceVariableMessage message = new WorkspaceVariableMessage(value);
        //message.setId(data.getId());
        message.setCreated(data.getCreated());

        return message;
    }
}
