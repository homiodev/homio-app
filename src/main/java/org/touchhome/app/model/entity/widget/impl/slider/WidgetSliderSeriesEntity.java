package org.touchhome.app.model.entity.widget.impl.slider;

import org.touchhome.bundle.api.entity.widget.HasSliderSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldNumber;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetSliderSeriesEntity extends WidgetSeriesEntity<WidgetSliderEntity> {

    public static final String PREFIX = "wgssls_";

    @UIField(order = 14, required = true)
    @UIFieldEntityByClassSelection(HasSliderSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 30)
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    public WidgetSliderSeriesEntity setMin(Integer value) {
        setJsonData("min", value);
        return this;
    }

    @UIField(order = 31)
    @UIFieldNumber(min = 0)
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    public WidgetSliderSeriesEntity setMax(Integer value) {
        setJsonData("max", value);
        return this;
    }

    @UIField(order = 32)
    @UIFieldNumber(min = 1)
    public Integer getStep() {
        return getJsonData("step", 1);
    }

    public WidgetSliderSeriesEntity setStep(Integer value) {
        setJsonData("step", value);
        return this;
    }

    @UIField(order = 15, type = UIFieldType.ColorPicker)
    public String getColor() {
        return getJsonData("color", "#FFFFFF");
    }

    public WidgetSliderSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
