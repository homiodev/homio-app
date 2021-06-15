package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.touchhome.bundle.api.entity.widget.HasLineChartSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldClassWithFeatureSelection;

import javax.persistence.Entity;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
public class WidgetLineChartSeriesEntity extends WidgetSeriesEntity<WidgetLineChartEntity> {

    public static final String PREFIX = "csw_";

    @UIField(order = 14, required = true)
    @UIFieldClassWithFeatureSelection(HasLineChartSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15, type = UIFieldType.ColorPicker)
    public String getColor() {
        return getJsonData("color", "#FFFFFF");
    }

    public WidgetLineChartSeriesEntity setColor(String value) {
        setJsonData("color", value);
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
    public AggregateFunction getAggregateFunction() {
        return getJsonDataEnum("aggrFn", AggregateFunction.mean);
    }

    public WidgetLineChartSeriesEntity setAggregateFunction(AggregateFunction value) {
        setJsonDataEnum("aggrFn", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Getter
    @AllArgsConstructor
    public enum AggregateFunction {
        mean(values -> {
            return values.stream().collect(Collectors.averagingDouble(value -> value)).floatValue();
        }),
        median(values -> {
            if (values.size() > 2) {
                values.sort(Float::compare);
                return values.get(values.size() / 2);
            }
            return values.isEmpty() ? 0F : values.get(0);
        }),
        first(values -> {
            return values.isEmpty() ? 0F : values.get(0);
        }),
        last(values -> {
            return values.isEmpty() ? 0F : values.get(values.size() - 1);
        }),
        max(values -> {
            return values.stream().max(Float::compareTo).orElse(0F);
        }),
        min(values -> {
            return values.stream().min(Float::compareTo).orElse(0F);
        }),
        sum(values -> values.stream().reduce(0F, Float::sum));

        private final Function<List<Float>, Float> aggregateFn;
    }
}
