package org.touchhome.app.model.entity.widget.impl.chart.bar;

import org.touchhome.app.model.entity.widget.impl.chart.TimeSeriesChartBaseEntity;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.app.rest.widget.WidgetChartsController;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.Entity;
import java.util.Collections;

@Entity
public class WidgetBarTimeChartEntity
        extends TimeSeriesChartBaseEntity<WidgetBarTimeChartEntity, WidgetBarTimeChartSeriesEntity,
        WidgetChartsController.BarChartDataset> {

    public static final String PREFIX = "wgtbtc_";

    @UIField(order = 38)
    public BarChartType getDisplayType() {
        return getJsonDataEnum("displayType", BarChartType.Horizontal);
    }

    public WidgetBarTimeChartEntity setDisplayType(BarChartType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 40)
    @UIFieldGroup("Axis")
    public String getAxisLabel() {
        return getJsonData("al", "example");
    }

    public WidgetBarTimeChartEntity setAxisLabel(String value) {
        setJsonData("al", value);
        return this;
    }

    @UIField(order = 52)
    @UIFieldSlider(min = 0, max = 4)
    public int getBorderWidth() {
        return getJsonData("bw", 1);
    }

    public WidgetBarTimeChartEntity setBorderWidth(int value) {
        setJsonData("bw", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-chart-bar";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public WidgetChartsController.BarChartDataset buildTargetDataset(TimeSeriesContext item) {
        WidgetBarTimeChartSeriesEntity seriesEntity = (WidgetBarTimeChartSeriesEntity) item.getSeriesEntity();
        WidgetChartsController.BarChartDataset dataset = new WidgetChartsController.BarChartDataset(item.getId());

        dataset.setBackgroundColor(Collections.singletonList(
                WidgetChartsController.getColorWithOpacity(seriesEntity.getColor(), seriesEntity.getChartColorOpacity())));
        dataset.setBorderColor(Collections.singletonList(seriesEntity.getColor()));
        dataset.setBorderWidth(this.getBorderWidth());
        dataset.setLabel(item.getSeriesEntity().getTitle());

        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getAggregationType()));
        }
        return dataset;
    }

    public enum BarChartType {
        Horizontal, Vertical
    }
}
