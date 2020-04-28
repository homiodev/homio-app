package org.touchhome.bundle.api.model.workspace.backup;

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
public class WorkspaceBackupValueEntity extends PureCrudEntity<WorkspaceBackupValueEntity> {

    @Column(nullable = false)
    private float value;

    @ManyToOne
    private WorkspaceBackupEntity workspaceBackupEntity;

    @Override
    public String getIdentifier() {
        return workspaceBackupEntity.getEntityID() + getCreationTime().getTime();
    }
}
