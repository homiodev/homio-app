package org.homio.app.model.entity.widget.impl.chart;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;

public interface HasMinMaxChartValue extends HasJsonData {

    @UIField(order = 1)
    @UIFieldGroup(order = 56, value = "CHART_AXIS", borderColor = "#0C73A6")
    default Integer getMin() {
        return getJsonData().has("min") ? getJsonData().getInt("min") : null;
    }

    default void setMin(Integer value) {
        setJsonData("min", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("CHART_AXIS")
    default Integer getMax() {
        return getJsonData().has("max") ? getJsonData().getInt("max") : null;
    }

    default void setMax(Integer value) {
        setJsonData("max", value);
    }
}
