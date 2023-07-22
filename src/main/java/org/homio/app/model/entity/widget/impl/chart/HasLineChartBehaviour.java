package org.homio.app.model.entity.widget.impl.chart;

import org.homio.api.EntityContextWidget.ChartType;
import org.homio.api.EntityContextWidget.Fill;
import org.homio.api.EntityContextWidget.PointStyle;
import org.homio.api.EntityContextWidget.Stepped;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.app.model.entity.widget.attributes.HasChartTimePeriod;

public interface HasLineChartBehaviour extends
    HasJsonData,
    HasMinMaxChartValue,
    HasChartTimePeriod {

    ChartType getChartType();

    @UIField(order = 20, isRevert = true)
    @UIFieldGroup(value = "CHART_UI", order = 54, borderColor = "#673AB7")
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default int getLineBorderWidth() {
        return getJsonData("lbw", 2);
    }

    default void setLineBorderWidth(int value) {
        setJsonData("lbw", value);
    }

    @UIField(order = 40, isRevert = true)
    @UIFieldGroup("CHART_UI")
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default Fill getLineFill() {
        return getJsonDataEnum("fill", Fill.Origin);
    }

    default void setLineFill(Fill value) {
        setJsonDataEnum("fill", value);
    }

    @UIField(order = 6, isRevert = true)
    @UIFieldGroup("CHART_UI")
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default Stepped getStepped() {
        return getJsonDataEnum("stpd", Stepped.False);
    }

    default void setStepped(Stepped value) {
        setJsonDataEnum("stpd", value);
    }

    @UIField(order = 3, isRevert = true)
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldGroup("CHART_UI")
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default int getTension() {
        return getJsonData("tns", 4);
    }

    default void setTension(int value) {
        setJsonData("tns", value);
    }

    @UIField(order = 5, isRevert = true)
    @UIFieldSlider(min = 10, max = 600)
    @UIFieldGroup("CHART_UI")
    default int getFetchDataFromServerInterval() {
        return getJsonData("fsfsi", 60);
    }

    default void setFetchDataFromServerInterval(int value) {
        setJsonData("fsfsi", value);
    }

    @UIField(order = 1, isRevert = true)
    @UIFieldGroup(value = "CHART_POINT", order = 58)
    @UIFieldSlider(min = 0, max = 4, step = 0.2)
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default double getPointRadius() {
        return getJsonData("prad", 0D);
    }

    default void setPointRadius(double value) {
        setJsonData("prad", value);
    }

    @UIField(order = 2, isRevert = true)
    @UIFieldGroup("CHART_POINT")
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    default PointStyle getPointStyle() {
        return getJsonDataEnum("pstyle", PointStyle.circle);
    }

    default void setPointStyle(PointStyle value) {
        setJsonDataEnum("pstyle", value);
    }

    @UIField(order = 3, isRevert = true)
    @UIFieldGroup("CHART_POINT")
    @UIFieldColorPicker
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    @UIFieldReadDefaultValue
    default String getPointBackgroundColor() {
        return getJsonData("pbg", UI.Color.WHITE);
    }

    default void setPointBackgroundColor(String value) {
        setJsonData("pbg", value);
    }

    @UIField(order = 4, isRevert = true)
    @UIFieldGroup("CHART_POINT")
    @UIFieldColorPicker
    @UIFieldShowOnCondition("return context.get('chartType') == 'line'")
    @UIFieldReadDefaultValue
    default String getPointBorderColor() {
        return getJsonData("pbc", UI.Color.PRIMARY_COLOR);
    }

    default void setPointBorderColor(String value) {
        setJsonData("pbc", value);
    }
}
