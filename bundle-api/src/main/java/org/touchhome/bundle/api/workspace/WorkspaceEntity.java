package org.touchhome.bundle.api.workspace;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;

@Getter
@Setter
@Entity
@Accessors(chain = true)
public class WorkspaceEntity extends BaseEntity<WorkspaceEntity> implements Comparable<WorkspaceEntity> {

    public static final String PREFIX = "ws_";

    @Lob
    @Column(length = 1048576)
    private String content;

    @Override
    public int compareTo(WorkspaceEntity o) {
        return this.getCreationTime().compareTo(o.getCreationTime());
    }

    @Override
    protected void validate() {
        if (getName() == null || getName().length() < 2 || getName().length() > 10) {
            throw new IllegalStateException("Workspace tab name must be between 2..10 characters");
        }
    }
}
