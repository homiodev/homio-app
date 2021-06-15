package org.touchhome.app.model.workspace;

import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;
import org.touchhome.app.repository.workspace.WorkspaceBroadcastRepository;
import org.touchhome.app.workspace.block.core.Scratch3EventsBlocks;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.HasLineChartSeries;
import org.touchhome.bundle.api.entity.widget.HasPieChartSeries;
import org.touchhome.bundle.api.entity.widget.HasPushButtonSeries;

import javax.persistence.*;
import java.util.*;

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
public class WorkspaceBroadcastEntity extends BaseEntity<WorkspaceBroadcastEntity> implements HasLineChartSeries,
        HasPieChartSeries, HasPushButtonSeries {

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

    @Override
    public double getPieSumChartSeries(EntityContext entityContext, Date from, Date to, String dateFromNow) {
        return entityContext.getBean(WorkspaceBroadcastRepository.class).getPieSumChartSeries(this, from, to);
    }

    @Override
    public double getPieCountChartSeries(EntityContext entityContext, Date from, Date to, String dateFromNow) {
        return entityContext.getBean(WorkspaceBroadcastRepository.class).getPieCountChartSeries(this, from, to);
    }

    @Override
    public void pushButton(EntityContext entityContext) {
        entityContext.getBean(Scratch3EventsBlocks.class).broadcastEvent(this);
    }

    @Override
    public Map<LineChartDescription, List<Object[]>> getLineChartSeries(EntityContext entityContext, JSONObject parameters, Date from, Date to, String dateFromNow) {
        return Collections.singletonMap(new LineChartDescription(), entityContext.getBean(WorkspaceBroadcastRepository.class).getLineChartSeries(this, from, to));
    }
}
