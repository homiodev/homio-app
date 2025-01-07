package org.homio.app.model.var;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.homio.api.storage.DataStorageEntity;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceVariableMessage extends DataStorageEntity {

  // @Setter need for deserializing from mongo
  private Object value;

  public static WorkspaceVariableMessage of(VariableBackup data, Object value) {
    WorkspaceVariableMessage message = new WorkspaceVariableMessage(value);
    message.setCreated(data.getCreated());

    return message;
  }

  public static WorkspaceVariableMessage average(List<WorkspaceVariableMessage> values) {
    double sum = 0.0;
    for (WorkspaceVariableMessage message : values) {
      sum += (Double) message.value;
    }
    return new WorkspaceVariableMessage(sum / values.size());
  }
}
