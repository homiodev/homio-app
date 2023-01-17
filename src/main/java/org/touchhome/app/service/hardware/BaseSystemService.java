package org.touchhome.app.service.hardware;

import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.touchhome.app.manager.common.EntityContextStorage;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ChartRequest;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;

@RequiredArgsConstructor
public abstract class BaseSystemService
        implements HasGetStatusValue, HasAggregateValueFromSeries, HasTimeValueSeries {

    private final String aggregateField;
    private final String entityID;
    private final String statusDesc;
    private final String aggrDesc;
    private final String timeSeriesDesc;

    @Override
    public void addUpdateValueListener(
            EntityContext entityContext,
            String key,
            JSONObject dynamicParameters,
            Consumer<Object> listener) {
        entityContext.event().addEventListener("cpu", key, listener);
    }

    @Override
    public @Nullable Object getAggregateValueFromSeries(
            @NotNull ChartRequest request,
            @NotNull AggregationType aggregationType,
            boolean exactNumber) {
        return EntityContextStorage.cpuStorage.aggregate(
                request.getFromTime(),
                request.getToTime(),
                null,
                null,
                aggregationType,
                false,
                aggregateField);
    }

    @Override
    public @NotNull List<Object[]> getTimeValueSeries(@NotNull ChartRequest request) {
        return EntityContextStorage.cpuStorage.getTimeSeries(
                request.getFromTime(), request.getToTime(), null, null, aggregateField);
    }

    @Override
    public String getEntityID() {
        return entityID;
    }

    @Override
    public String getGetStatusDescription() {
        return statusDesc;
    }

    @Override
    public String getAggregateValueDescription() {
        return aggrDesc;
    }

    @Override
    public String getTimeValueSeriesDescription() {
        return timeSeriesDesc;
    }
}
