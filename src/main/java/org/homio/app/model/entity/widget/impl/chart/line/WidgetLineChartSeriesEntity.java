package org.homio.app.model.entity.widget.impl.chart.line;

import jakarta.persistence.Entity;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnoreParent;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetLineChartSeriesEntity extends WidgetSeriesEntity<WidgetLineChartEntity>
        implements HasChartDataSource {

    @Override
    public String getDefaultName() {
        return null;
    }

    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
    @UIFieldBeanSelection(value = HasTimeValueSeries.class, lazyLoading = true)
    @UIFieldGroup(value = "CHART", order = 50, borderColor = "#9C27B0")
    @UIFieldIgnoreParent
    public String getChartDataSource() {
        return getJsonData("chartDS");
    }

    @Override
    protected void beforePersist() {
        HasChartDataSource.randomColor(this);
    }

    @Override
    protected String getSeriesPrefix() {
        return "line";
    }
}
