package org.homio.app.model.entity.widget.impl.chart;

import org.homio.bundle.api.entity.HasJsonData;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;

public interface HasAxis extends HasJsonData {

    @UIField(order = 80)
    @UIFieldGroup("CHART_AXIS")
    default Boolean getShowAxisX() {
        return getJsonData("showAxisX", Boolean.TRUE);
    }

    default void setShowAxisX(Boolean value) {
        setJsonData("showAxisX", value);
    }

    @UIField(order = 81)
    @UIFieldGroup("CHART_AXIS")
    default Boolean getShowAxisY() {
        return getJsonData("showAxisY", Boolean.TRUE);
    }

    default void setShowAxisY(Boolean value) {
        setJsonData("showAxisY", value);
    }

    @UIField(order = 84)
    @UIFieldGroup("CHART_AXIS")
    default String getAxisLabelX() {
        return getJsonData("axisLabelX");
    }

    default void setAxisLabelX(String value) {
        setJsonData("axisLabelX", value);
    }

    @UIField(order = 85)
    @UIFieldGroup("CHART_AXIS")
    default String getAxisLabelY() {
        return getJsonData("axisLabelY");
    }

    default void setAxisLabelY(String value) {
        setJsonData("axisLabelY", value);
    }

    @UIField(order = 90)
    @UIFieldGroup("CHART_AXIS")
    default String getAxisDateFormat() {
        return getJsonData("timeF");
    }

    default void setAxisDateFormat(String value) {
        setJsonData("timeF", value);
    }
}
