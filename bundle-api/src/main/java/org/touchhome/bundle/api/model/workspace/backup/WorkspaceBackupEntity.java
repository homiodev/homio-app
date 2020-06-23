package org.touchhome.bundle.api.model.workspace.backup;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.*;
import java.util.List;

@Getter
@Entity
@Accessors(chain = true)
@NamedQueries({
        @NamedQuery(name = "WorkspaceBackupEntity.fetchLastValue",
                query = "SELECT e.value FROM WorkspaceBackupValueCrudEntity e WHERE e.workspaceBackupEntity = :source ORDER BY e.creationTime DESC"),
        @NamedQuery(name = "WorkspaceBackupEntity.fetchValues",
                query = "SELECT e.creationTime, e.value FROM WorkspaceBackupValueCrudEntity e WHERE e.workspaceBackupEntity = :source AND e.creationTime >= :from AND e.creationTime <= :to ORDER BY e.creationTime"),
        @NamedQuery(name = "WorkspaceBackupEntity.fetchSum",
                query = "SELECT SUM(e.value) FROM WorkspaceBackupValueCrudEntity e WHERE e.workspaceBackupEntity = :source AND e.creationTime >= :from AND e.creationTime <= :to"),
        @NamedQuery(name = "WorkspaceBackupEntity.fetchCount",
                query = "SELECT COUNT(e) FROM WorkspaceBackupValueCrudEntity e WHERE e.workspaceBackupEntity = :source AND e.creationTime >= :from AND e.creationTime <= :to"),
        @NamedQuery(name = "WorkspaceBackupEntity.fetchMinDate",
                query = "SELECT MIN(e.creationTime) FROM WorkspaceBackupValueCrudEntity e WHERE e.workspaceBackupEntity = :source GROUP BY e.workspaceBackupEntity")
})
public class WorkspaceBackupEntity extends BaseEntity<WorkspaceBackupEntity> {

    public static final String PREFIX = "wb_";

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    private WorkspaceBackupGroupEntity workspaceBackupGroupEntity;

    @Setter
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "workspaceBackupEntity", cascade = CascadeType.REMOVE)
    private List<WorkspaceBackupValueCrudEntity> values;

    @Override
    public String getTitle() {
        return "Backup[" + workspaceBackupGroupEntity.getName() + "] - variable: " + this.getName();
    }
}
