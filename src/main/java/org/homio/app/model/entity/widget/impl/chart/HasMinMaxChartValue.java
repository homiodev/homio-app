package org.homio.app.model.entity.widget.impl.chart;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;

public interface HasMinMaxChartValue extends HasJsonData {

    @UIField(order = 1)
    @UIFieldGroup(order = 56, value = "CHART_AXIS", borderColor = "#0C73A6")
    default int getMin() {
        return getJsonData("min", 0);
    }

    default void setMin(int value) {
        setJsonData("min", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("CHART_AXIS")
    default int getMax() {
        return getJsonData("max", 100);
    }

    default void setMax(int value) {
        setJsonData("max", value);
    }
}
