package org.touchhome.app.model.workspace;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.touchhome.app.repository.workspace.WorkspaceBroadcastRepository;
import org.touchhome.app.workspace.block.core.Scratch3EventsBlocks;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.*;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Entity
public class WorkspaceBroadcastEntity extends BaseEntity<WorkspaceBroadcastEntity> implements HasTimeValueSeries,
        HasAggregateValueFromSeries, HasPushButtonSeries {

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
    public void pushButton(EntityContext entityContext) {
        entityContext.getBean(Scratch3EventsBlocks.class).broadcastEvent(this);
    }

    @Override
    public Float getAggregateValueFromSeries(ChartRequest request, AggregationType aggregationType) {
        return request.getEntityContext().getBean(WorkspaceBroadcastRepository.class)
                .getValue(this, request);
    }

    @Override
    public @NotNull List<Object[]> getTimeValueSeries(@NotNull ChartRequest request) {
        return request.getEntityContext().getBean(WorkspaceBroadcastRepository.class)
                .getLineChartSeries(this, request);
    }
}
