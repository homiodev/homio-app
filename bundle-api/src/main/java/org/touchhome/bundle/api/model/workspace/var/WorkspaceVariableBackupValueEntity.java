package org.touchhome.bundle.api.model.workspace.var;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.PureCrudEntity;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(indexes = {@Index(columnList = "creationTime")})
public class WorkspaceVariableBackupValueEntity extends PureCrudEntity<WorkspaceVariableBackupValueEntity> {

    @Column(nullable = false)
    private float value;

    @ManyToOne
    private WorkspaceVariableEntity workspaceVariableEntity;

    @Override
    public String getIdentifier() {
        return workspaceVariableEntity.getEntityID() + getCreationTime().getTime();
    }
}
