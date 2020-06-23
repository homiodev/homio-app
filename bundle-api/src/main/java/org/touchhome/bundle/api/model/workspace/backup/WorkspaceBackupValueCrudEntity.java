package org.touchhome.bundle.api.model.workspace.backup;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.CrudEntity;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(indexes = {@Index(columnList = "creationTime")})
public class WorkspaceBackupValueCrudEntity extends CrudEntity<WorkspaceBackupValueCrudEntity> {

    @Column(nullable = false)
    private float value;

    @ManyToOne
    private WorkspaceBackupEntity workspaceBackupEntity;

    @Override
    public String getIdentifier() {
        return workspaceBackupEntity.getEntityID() + getCreationTime().getTime();
    }
}
