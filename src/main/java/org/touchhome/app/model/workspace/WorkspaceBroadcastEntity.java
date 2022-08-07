package org.touchhome.app.model.workspace;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.touchhome.app.repository.workspace.WorkspaceBroadcastRepository;
import org.touchhome.app.workspace.block.core.Scratch3EventsBlocks;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.*;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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
    public void firePushButton(EntityContext entityContext, JSONObject dynamicParameters) {
        entityContext.getBean(Scratch3EventsBlocks.class).broadcastEvent(this);
    }

    @Override
    public Object getAggregateValueFromSeries(ChartRequest request, AggregationType aggregationType, boolean filterOnlyNumbers) {
        return request.getEntityContext().getBean(WorkspaceBroadcastRepository.class)
                .getValue(this, request);
    }

    @Override
    public void addUpdateValueListener(EntityContext entityContext, String key, JSONObject dynamicParameters,
                                       Consumer<Object> listener) {
        entityContext.event().addEventListener(getEntityID(), listener);
    }

    @Override
    public @NotNull List<Object[]> getTimeValueSeries(@NotNull ChartRequest request) {
        return request.getEntityContext().getBean(WorkspaceBroadcastRepository.class)
                .getTimeValueSeries(this, request);
    }
}
