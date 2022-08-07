package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasTimeValueSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetLineChartSeriesEntity extends WidgetSeriesEntity<WidgetLineChartEntity> {

    public static final String PREFIX = "wgslcs_";

    @UIField(order = 14, required = true)
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15)
    @UIFieldGroup("Chart line")
    @UIFieldColorPicker
    public String getChartColor() {
        return getJsonData("bc", "#FFFFFF");
    }

    public WidgetLineChartSeriesEntity setChartColor(String value) {
        setJsonData("bc", value);
        return this;
    }

    @UIField(order = 16)
    @UIFieldSlider(min = 1, max = 254, step = 5)
    @UIFieldGroup("Chart line")
    public int getChartColorOpacity() {
        return getJsonData("bco", 120);
    }

    public void setChartColorOpacity(int value) {
        setJsonData("bco", value);
    }

    @UIField(order = 17)
    @UIFieldSlider(min = 0, max = 10)
    @UIFieldGroup("Chart line")
    public int getTension() {
        return getJsonData("tns", 4);
    }

    public WidgetLineChartSeriesEntity setTension(int value) {
        setJsonData("tns", value);
        return this;
    }

    @UIField(order = 17)
    @UIFieldGroup("Chart line")
    public Stepped getStepped() {
        return getJsonDataEnum("stpd", Stepped.False);
    }

    public WidgetLineChartSeriesEntity setStepped(Stepped value) {
        setJsonDataEnum("stpd", value);
        return this;
    }

    @UIField(order = 20)
    public Boolean getFillMissingValues() {
        return getJsonData("fillMis", false);
    }

    public WidgetLineChartSeriesEntity setFillMissingValues(Boolean value) {
        setJsonData("fillMis", value);
        return this;
    }

    @UIField(order = 25)
    public AggregationType getAggregationType() {
        return getJsonDataEnum("aggr", AggregationType.Last);
    }

    public WidgetLineChartSeriesEntity setAggregationType(AggregationType value) {
        setJsonData("aggr", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("bc")) {
            setChartColor(UI.Color.random());
        }
    }

    @RequiredArgsConstructor
    public enum Stepped {
        False(false),
        True(true),
        Before("before"),
        After("after"),
        Middle("middle");

        @Getter
        private final Object value;
    }
}
