package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.TimeSeriesChartBaseEntity;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.app.rest.widget.WidgetChartsController;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasTimeValueSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;
import java.util.Set;

@Getter
@Setter
@Entity
public class WidgetMiniCardChartEntity
        extends TimeSeriesChartBaseEntity<WidgetMiniCardChartEntity, WidgetMiniCardChartSeriesEntity,
        WidgetChartsController.TimeSeriesDataset> {

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

    @UIField(order = 1, type = UIFieldType.IconPicker)
    @UIFieldGroup(value = "Icon", order = 2)
    public String getIcon() {
        return getJsonData("icon", "fas fa-temperature-half");
    }

    @UIField(order = 2, type = UIFieldType.ColorPicker)
    @UIFieldGroup("Icon")
    public String getIconColor() {
        return getJsonData("ic", "#44739E");
    }

    // ### chart group

    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
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
    @UIFieldGroup("Chart")
    public AggregationType getAggregationType() {
        return getJsonDataEnum("aggr", AggregationType.Average);
    }

    @UIField(order = 4)
    @UIFieldGroup("Chart")
    public Boolean getFillMissingValues() {
        return getJsonData("fillMis", false);
    }

    @UIField(order = 5)
    @UIFieldGroup("Chart")
    public ChartType getChartType() {
        return getJsonDataEnum("ct", ChartType.line);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public enum ChartType {
        line, bar, none
    }

    // #### Chart UI look and feel

    @UIField(order = 1, type = UIFieldType.ColorPicker)
    @UIFieldGroup(value = "Chart ui", order = 4)
    public String getChartColor() {
        return getJsonData("bc", "#FFFFFF");
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 1, max = 254, step = 5)
    @UIFieldGroup("Chart ui")
    public int getColorOpacity() {
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
        return getJsonData("hts", 30);
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("bc")) {
            setChartColor(UI.Color.random());
        }
    }

    @Override
    public boolean fillMissingValues(WidgetSeriesEntity seriesEntity) {
        return ((WidgetLineChartSeriesEntity) seriesEntity).getFillMissingValues();
    }

    @Override
    public WidgetChartsController.TimeSeriesDataset buildTargetDataset(TimeSeriesContext item) {
        WidgetLineChartSeriesEntity seriesEntity = (WidgetLineChartSeriesEntity) item.getSeriesEntity();
        WidgetChartsController.TimeSeriesDataset dataset = new WidgetChartsController.TimeSeriesDataset(item.getId(),
                seriesEntity.getTitle(), seriesEntity.getChartColor(), seriesEntity.getColorOpacity(),
                seriesEntity.getTension() / 10D, seriesEntity.getStepped().getValue());
        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getAggregationType()));
        }
        return dataset;
    }

    public void setDataSource(String value) {
        setJsonData("ds", value);
    }

    public void setChartHeight(int value) {
        setJsonData("hts", value);
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    public void setIconColor(String value) {
        setJsonData("ic", value);
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

    public void getChartType(ChartType value) {
        setJsonData("ct", value);
    }

    public void setChartColor(String value) {
        setJsonData("bc", value);
    }

    public void setColorOpacity(int value) {
        setJsonData("bco", value);
    }

    public void setTension(int value) {
        setJsonData("tns", value);
    }

    // ### ignore UIFields!!!

    @Override
    @UIFieldIgnore
    public TimePeriod getTimePeriod() {
        return super.getTimePeriod();
    }

    @Override
    @UIFieldIgnore
    public Boolean getLegendShow() {
        return super.getLegendShow();
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
        return super.getShowAxisX();
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowAxisY() {
        return super.getShowAxisY();
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
    @UIFieldIgnore
    public Boolean getShowTimeButtons() {
        return super.getShowTimeButtons();
    }

    @Override
    @UIFieldIgnore
    public Set<WidgetMiniCardChartSeriesEntity> getSeries() {
        return super.getSeries();
    }
}
