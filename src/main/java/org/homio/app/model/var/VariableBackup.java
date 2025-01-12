package org.homio.app.model.var;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "variable_backup")
public class VariableBackup {

    @Id
    private Integer id;

    private long created;

    private String value;

    @ManyToOne(fetch = FetchType.LAZY, targetEntity = WorkspaceVariable.class)
    private WorkspaceVariable workspaceVariable;

    public VariableBackup(Integer id, WorkspaceVariable variable, WorkspaceVariableMessage message) {
        this.id = id;
        this.workspaceVariable = variable;
        this.created = message.getCreated();
        this.value = message.getValue().toString();
    }
}
