package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.TimeSeriesChartBaseEntity;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.app.rest.widget.WidgetChartsController;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasTimeValueAndLastValueSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

import javax.persistence.Entity;
import java.util.Set;

@Getter
@Setter
@Entity
public class WidgetMiniCardChartEntity
        extends TimeSeriesChartBaseEntity<WidgetMiniCardChartEntity, WidgetMiniCardChartSeriesEntity,
        WidgetChartsController.TimeSeriesDataset> implements HasDynamicParameterFields {

    public static final String PREFIX = "wgtmcc_";

    @Override
    public String getImage() {
        return "fas fa-diagram-successor";
    }

    // ### text group

    @Override
    @UIField(order = 1)
    @UIFieldGroup(value = "Text", order = 1)
    public String getTitle() {
        return super.getTitle();
    }

    @UIField(order = 2)
    @UIFieldGroup("Text")
    public String getUnit() {
        return getJsonData("unit", "°C");
    }

    // ### icon group

    @UIField(order = 1)
    @UIFieldIconPicker(allowThreshold = true, allowEmptyIcon = true)
    @UIFieldGroup(value = "UI", order = 2)
    public String getIcon() {
        return getJsonData("icon", "fas fa-temperature-half");
    }

    @UIField(order = 2)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    public String getIconColor() {
        return getJsonData("iconColor", "#44739E");
    }

    @UIField(order = 3)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    public String getTextColor() {
        return getJsonData("txtCol", UI.Color.WHITE);
    }

    // ### chart group

    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasTimeValueAndLastValueSeries.class)
    @UIFieldGroup(value = "Chart", order = 3)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 1, max = 72)
    @UIFieldGroup("Chart")
    public int getHoursToShow() {
        return getJsonData("hts", 24);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 0.1, max = 60, step = 0.2)
    @UIFieldGroup("Chart")
    public double getPointsPerHour() {
        return getJsonData("pph", 0.5D);
    }

    @UIField(order = 5)
    @UIFieldGroup("Chart")
    public AggregationType getAggregationType() {
        return getJsonDataEnum("aggr", AggregationType.Average);
    }

    @UIField(order = 6)
    @UIFieldGroup("Chart")
    public Boolean getFillMissingValues() {
        return getJsonData("fillMis", false);
    }

    @UIField(order = 7)
    @UIFieldGroup("Chart")
    public HasChartDataSource.ChartType getChartType() {
        return getJsonDataEnum("ct", HasChartDataSource.ChartType.line);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    // #### Chart UI look and feel

    @UIField(order = 1)
    @UIFieldGroup(value = "Chart ui", order = 4)
    @UIFieldColorPicker(allowThreshold = true)
    public String getChartColor() {
        return getJsonData("bc", "#FFFFFF");
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 1, max = 254, step = 5)
    @UIFieldGroup("Chart ui")
    public int getChartColorOpacity() {
        return getJsonData("bco", 120);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldGroup("Chart ui")
    public int getTension() {
        return getJsonData("tns", 4);
    }

    @UIField(order = 4)
    @UIFieldSlider(min = 20, max = 100)
    @UIFieldGroup("Chart ui")
    public int getChartHeight() {
        return getJsonData("ch", 30);
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("bc")) {
            setChartColor(UI.Color.random());
        }
    }

    @Override
    public boolean fillMissingValues(WidgetSeriesEntity seriesEntity) {
        return getJsonData("fillMis", true);
    }

    @Override
    public WidgetChartsController.TimeSeriesDataset buildTargetDataset(TimeSeriesContext item) {
        WidgetChartsController.TimeSeriesDataset dataset =
                new WidgetChartsController.TimeSeriesDataset(item.getId(), null, getChartColor(), getChartColorOpacity(),
                        getTension() / 10D, false);
        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), getAggregationType()));
        }
        return dataset;
    }

    public void setDataSource(String value) {
        setJsonData("ds", value);
    }

    public void setChartHeight(int value) {
        setJsonData("ch", value);
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    public void setIconColor(String value) {
        setJsonData("iconColor", value);
    }

    public void setUnit(String value) {
        setJsonData("unit", value);
    }

    public void setHoursToShow(int value) {
        setJsonData("hts", value);
    }

    public void setAggregationType(AggregationType value) {
        setJsonData("aggr", value);
    }

    public void setFillMissingValues(Boolean value) {
        setJsonData("fillMis", value);
    }

    public void getChartType(HasChartDataSource.ChartType value) {
        setJsonData("ct", value);
    }

    public void setChartColor(String value) {
        setJsonData("bc", value);
    }

    public void setChartColorOpacity(int value) {
        setJsonData("bco", value);
    }

    public void setTension(int value) {
        setJsonData("tns", value);
    }

    public void setPointsPerHour(double value) {
        setJsonData("pph", value);
    }

    public void setTextColor(String value) {
        setJsonData("txtCol", value);
    }

    // ### ignore UIFields!!!

    @Override
    @UIFieldIgnore
    public TimePeriod getTimePeriod() {
        return TimePeriod.All;
    }

    @Override
    @UIFieldIgnore
    public Boolean getLegendShow() {
        return false;
    }

    @Override
    @UIFieldIgnore
    public LegendPosition getLegendPosition() {
        return super.getLegendPosition();
    }

    @Override
    @UIFieldIgnore
    public LegendAlign getLegendAlign() {
        return super.getLegendAlign();
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowAxisX() {
        return false;
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowAxisY() {
        return false;
    }

    @Override
    @UIFieldIgnore
    public String getAxisLabelX() {
        return super.getAxisLabelX();
    }

    @Override
    @UIFieldIgnore
    public String getAxisLabelY() {
        return super.getAxisLabelY();
    }

    @Override
    public boolean getShowDataLabels() {
        return getJsonData("sdl", false);
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowTimeButtons() {
        return false;
    }

    @Override
    @UIFieldIgnore
    public Set<WidgetMiniCardChartSeriesEntity> getSeries() {
        return super.getSeries();
    }

    public enum GroupBy {
        interval, date, hour
    }

    @Override
    public void setDynamicParameterFieldsHolder(JSONObject value) {
        super.setJsonData("dsp", value);
    }

    @Override
    public JSONObject getDynamicParameterFieldsHolder() {
        return getJsonData().optJSONObject("dsp");
    }
}
