package org.touchhome.app.model.entity.widget.impl;

import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

public interface HasLineChartDataSource<T> extends HasChartDataSource<T> {

    ChartType getChartType();

    @UIField(order = 20)
    @UIFieldGroup("Chart ui")
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldShowOnCondition("eval(\"this.get('chartType') == 'line'\")")
    default int getLineBorderWidth() {
        return getJsonData("lbw", 3);
    }

    default void setLineBorderWidth(int value) {
        setJsonData("lbw", value);
    }

    @UIField(order = 40)
    @UIFieldGroup("Chart ui")
    @UIFieldShowOnCondition("eval(\"this.get('chartType') == 'line'\")")
    default Fill getLineFill() {
        return getJsonDataEnum("fill", Fill.Origin);
    }

    default void setLineFill(Fill value) {
        setJsonDataEnum("fill", value);
    }

    @UIField(order = 6)
    @UIFieldGroup(value = "Chart ui")
    @UIFieldShowOnCondition("eval(\"this.get('chartType') == 'line'\")")
    default Stepped getStepped() {
        return getJsonDataEnum("stpd", Stepped.False);
    }

    default void setStepped(Stepped value) {
        setJsonDataEnum("stpd", value);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldGroup("Chart ui")
    @UIFieldShowOnCondition("eval(\"this.get('chartType') == 'line'\")")
    default int getTension() {
        return getJsonData("tns", 4);
    }

    default void setTension(int value) {
        setJsonData("tns", value);
    }

    enum Stepped {
        False,
        True,
        Before,
        After,
        Middle;
    }

    enum Fill {
        Start,
        End,
        Origin,
        Disabled,
        Stack
    }
}
