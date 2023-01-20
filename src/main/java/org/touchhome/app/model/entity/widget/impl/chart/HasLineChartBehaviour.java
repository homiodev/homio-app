package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.app.model.entity.widget.impl.HasChartTimePeriod;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.condition.UIFieldShowOnCondition;

public interface HasLineChartBehaviour extends HasJsonData, HasMinMaxChartValue, HasChartTimePeriod {

    HasChartDataSource.ChartType getChartType();

    @UIField(order = 20)
    @UIFieldGroup(value = "Chart ui", order = 11, borderColor = "#673AB7")
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default int getLineBorderWidth() {
        return getJsonData("lbw", 3);
    }

    default void setLineBorderWidth(int value) {
        setJsonData("lbw", value);
    }

    @UIField(order = 40)
    @UIFieldGroup("Chart ui")
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default Fill getLineFill() {
        return getJsonDataEnum("fill", Fill.Origin);
    }

    default void setLineFill(Fill value) {
        setJsonDataEnum("fill", value);
    }

    @UIField(order = 6)
    @UIFieldGroup(value = "Chart ui")
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default Stepped getStepped() {
        return getJsonDataEnum("stpd", Stepped.False);
    }

    default void setStepped(Stepped value) {
        setJsonDataEnum("stpd", value);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldGroup("Chart ui")
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default int getTension() {
        return getJsonData("tns", 4);
    }

    default void setTension(int value) {
        setJsonData("tns", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Chart point", order = 5)
    @UIFieldSlider(min = 0, max = 4, step = 0.2)
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default double getPointRadius() {
        return getJsonData("prad", 0D);
    }

    default void setPointRadius(double value) {
        setJsonData("prad", value);
    }

    @UIField(order = 2)
    @UIFieldGroup(value = "Chart point")
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default PointStyle getPointStyle() {
        return getJsonDataEnum("pstyle", PointStyle.circle);
    }

    default void setPointStyle(PointStyle value) {
        setJsonDataEnum("pstyle", value);
    }

    @UIField(order = 3, isRevert = true)
    @UIFieldGroup(value = "Chart point")
    @UIFieldColorPicker
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default String getPointBackgroundColor() {
        return getJsonData("pbg", UI.Color.WHITE);
    }

    default void setPointBackgroundColor(String value) {
        setJsonData("pbg", value);
    }

    @UIField(order = 4, isRevert = true)
    @UIFieldGroup(value = "Chart point")
    @UIFieldColorPicker
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default String getPointBorderColor() {
        return getJsonData("pbc", UI.Color.PRIMARY_COLOR);
    }

    default void setPointBorderColor(String value) {
        setJsonData("pbc", value);
    }

    enum PointStyle {
        circle,
        cross,
        crossRot,
        dash,
        line,
        rect,
        rectRounded,
        rectRot,
        star,
        triangle
    }

    enum Stepped {
        False,
        True,
        Before,
        After,
        Middle
    }

    enum Fill {
        Start,
        End,
        Origin,
        Disabled,
        Stack
    }
}
