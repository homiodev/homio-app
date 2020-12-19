package org.touchhome.app.model.workspace;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.entity.CrudEntity;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(indexes = {@Index(columnList = "creationTime")})
public class WorkspaceBroadcastValueCrudEntity extends CrudEntity<WorkspaceBroadcastValueCrudEntity> {

    @ManyToOne
    private WorkspaceBroadcastEntity workspaceBroadcastEntity;

    @Override
    public String getIdentifier() {
        return workspaceBroadcastEntity.getEntityID() + getCreationTime().getTime();
    }
}
