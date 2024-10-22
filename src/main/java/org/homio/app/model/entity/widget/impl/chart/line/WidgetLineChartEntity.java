package org.homio.app.model.entity.widget.impl.chart.line;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.homio.api.ContextWidget.ChartType;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.app.model.entity.widget.UIFieldPadding;
import org.homio.app.model.entity.widget.attributes.HasPadding;
import org.homio.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.homio.app.model.entity.widget.impl.chart.HasAxis;
import org.homio.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.homio.app.model.entity.widget.impl.chart.HasLineChartBehaviour;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity
        extends ChartBaseEntity<WidgetLineChartEntity, WidgetLineChartSeriesEntity>
        implements HasLineChartBehaviour, HasHorizontalLine, HasAxis, HasPadding {

    @Override
    public @NotNull String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "line";
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    public ChartType getChartType() {
        return ChartType.line;
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
