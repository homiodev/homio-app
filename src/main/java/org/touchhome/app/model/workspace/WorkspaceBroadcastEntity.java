package org.touchhome.app.model.workspace;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.entity.BaseEntity;

import javax.persistence.*;
import java.util.Set;

@Getter
@Entity
@NamedQueries({
        @NamedQuery(name = "WorkspaceBroadcastEntity.fetchValues",
                query = "SELECT e.creationTime FROM WorkspaceBroadcastValueCrudEntity e " +
                        "WHERE e.workspaceBroadcastEntity = :source " +
                        "AND e.creationTime >= :from " +
                        "AND e.creationTime <= :to " +
                        "ORDER BY e.creationTime"),
        @NamedQuery(name = "WorkspaceBroadcastEntity.fetchCount",
                query = "SELECT COUNT(e) FROM WorkspaceBroadcastValueCrudEntity e " +
                        "WHERE e.workspaceBroadcastEntity = :source " +
                        "AND e.creationTime >= :from " +
                        "AND e.creationTime <= :to")
})
public class WorkspaceBroadcastEntity extends BaseEntity<WorkspaceBroadcastEntity> {

    public static final String PREFIX = "brc_";

    @Setter
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "workspaceBroadcastEntity", cascade = CascadeType.REMOVE)
    private Set<WorkspaceBroadcastValueCrudEntity> values;

    @Override
    public String getTitle() {
        return "Broadcast event: " + this.getName();
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
