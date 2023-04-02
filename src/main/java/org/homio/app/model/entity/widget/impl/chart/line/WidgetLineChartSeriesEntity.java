package org.homio.app.model.entity.widget.impl.chart.line;

import javax.persistence.Entity;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.homio.bundle.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldIgnoreParent;
import org.homio.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

@Entity
public class WidgetLineChartSeriesEntity extends WidgetSeriesEntity<WidgetLineChartEntity>
    implements HasChartDataSource {

    public static final String PREFIX = "wgslcs_";

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        HasChartDataSource.randomColor(this);
    }

    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
    @UIFieldBeanSelection(value = HasTimeValueSeries.class, lazyLoading = true)
    @UIFieldGroup(value = "Chart", order = 10, borderColor = "#9C27B0")
    @UIFieldIgnoreParent
    public String getChartDataSource() {
        return getJsonData("chartDS");
    }
}
