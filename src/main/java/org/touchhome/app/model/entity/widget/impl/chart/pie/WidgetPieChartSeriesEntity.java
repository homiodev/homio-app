package org.touchhome.app.model.entity.widget.impl.chart.pie;

import org.touchhome.bundle.api.entity.widget.HasPieChartSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldClassWithFeatureSelection;

import javax.persistence.Entity;

@Entity
public class WidgetPieChartSeriesEntity extends WidgetSeriesEntity<WidgetPieChartEntity> {

    public static final String PREFIX = "piesw_";

    @UIField(order = 14, required = true)
    @UIFieldClassWithFeatureSelection(HasPieChartSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15, type = UIFieldType.ColorPicker)
    public String getColor() {
        return getJsonData("color", "#FFFFFF");
    }

    public WidgetPieChartSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
