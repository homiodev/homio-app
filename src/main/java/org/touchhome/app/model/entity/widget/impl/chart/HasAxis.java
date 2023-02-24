package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

public interface HasAxis extends HasJsonData {

    @UIField(order = 80)
    @UIFieldGroup("Chart axis")
    default Boolean getShowAxisX() {
        return getJsonData("showAxisX", Boolean.TRUE);
    }

    default void setShowAxisX(Boolean value) {
        setJsonData("showAxisX", value);
    }

    @UIField(order = 81)
    @UIFieldGroup("Chart axis")
    default Boolean getShowAxisY() {
        return getJsonData("showAxisY", Boolean.TRUE);
    }

    default void setShowAxisY(Boolean value) {
        setJsonData("showAxisY", value);
    }

    @UIField(order = 84)
    @UIFieldGroup("Chart axis")
    default String getAxisLabelX() {
        return getJsonData("axisLabelX");
    }

    default void setAxisLabelX(String value) {
        setJsonData("axisLabelX", value);
    }

    @UIField(order = 85)
    @UIFieldGroup("Chart axis")
    default String getAxisLabelY() {
        return getJsonData("axisLabelY");
    }

    default void setAxisLabelY(String value) {
        setJsonData("axisLabelY", value);
    }

    @UIField(order = 90)
    @UIFieldGroup("Chart axis")
    default String getAxisDateFormat() {
        return getJsonData("timeF");
    }

    default void setAxisDateFormat(String value) {
        setJsonData("timeF", value);
    }
}
