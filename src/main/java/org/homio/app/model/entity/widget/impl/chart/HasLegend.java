package org.homio.app.model.entity.widget.impl.chart;

import org.homio.bundle.api.EntityContextWidget.LegendAlign;
import org.homio.bundle.api.EntityContextWidget.LegendPosition;
import org.homio.bundle.api.entity.HasJsonData;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;

public interface HasLegend extends HasJsonData {

    @UIField(order = 70)
    @UIFieldGroup(value = "LEGEND", order = 20, borderColor = "#77AD2F")
    default Boolean isShowLegend() {
        return getJsonData("ls", Boolean.FALSE);
    }

    default void setShowLegend(Boolean value) {
        setJsonData("ls", value);
    }

    @UIField(order = 71)
    @UIFieldGroup("LEGEND")
    default LegendPosition getLegendPosition() {
        return getJsonDataEnum("lp", LegendPosition.top);
    }

    default void setLegendPosition(LegendPosition value) {
        setJsonData("lp", value);
    }

    @UIField(order = 72)
    @UIFieldGroup("LEGEND")
    default LegendAlign getLegendAlign() {
        return getJsonDataEnum("la", LegendAlign.center);
    }

    default void setLegendAlign(LegendAlign value) {
        setJsonData("la", value);
    }
}
