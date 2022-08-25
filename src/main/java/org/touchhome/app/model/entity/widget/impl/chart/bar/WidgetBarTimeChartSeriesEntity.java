package org.touchhome.app.model.entity.widget.impl.chart.bar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.rest.widget.ChartDataset;
import org.touchhome.app.rest.widget.EvaluateDatesAndValues;
import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

import javax.persistence.Entity;

@Entity
public class WidgetBarTimeChartSeriesEntity extends WidgetSeriesEntity<WidgetBarTimeChartEntity>
        implements HasChartDataSource<WidgetBarTimeChartEntity> {

    public static final String PREFIX = "wgsbtcs_";

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public int getHoursToShow() {
        throw new RuntimeException("MNC");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public int getPointsPerHour() {
        throw new RuntimeException("MNC");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public ChartType getChartType() {
        throw new RuntimeException("MNC");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public int getTension() {
        throw new RuntimeException("MNC");
    }

    @Override
    public ChartDataset buildTargetDataset(TimeSeriesContext item) {
        WidgetBarTimeChartSeriesEntity seriesEntity = (WidgetBarTimeChartSeriesEntity) item.getSeriesEntity();
        ChartDataset dataset = new ChartDataset(item.getId());

       // dataset.setBackgroundColor(Collections.singletonList(
         //       WidgetChartsController.getColorWithOpacity(seriesEntity.getChartColor(), seriesEntity.getChartColorOpacity())));
        // dataset.setBorderColor(Collections.singletonList(seriesEntity.getChartColor()));

       // dataset.setBorderWidth(getWidgetEntity().getBorderWidth());
    //    dataset.setLabel(item.getSeriesEntity().getTitle());

        if (item.getValues() != null && !item.getValues().isEmpty()) {
            dataset.setData(EvaluateDatesAndValues.aggregate(item.getValues(), seriesEntity.getChartAggregationType()));
        }
        return dataset;
    }

    @Override
    protected void beforePersist() {
        setInitChartColor(UI.Color.random());
    }
}
