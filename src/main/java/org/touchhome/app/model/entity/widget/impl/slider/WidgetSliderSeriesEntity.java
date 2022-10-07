package org.touchhome.app.model.entity.widget.impl.slider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.*;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetSliderSeriesEntity extends WidgetSeriesEntity<WidgetSliderEntity>
        implements HasSingleValueDataSource, HasIcon, HasValueTemplate, HasName, HasTextConverter {

    public static final String PREFIX = "wgssls_";

    @Override
    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldGroup(value = "Value", order = 1)
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    @Override
    @UIField(order = 2, required = true)
    @UIFieldGroup(value = "Value")
    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    public String getSetValueDataSource() {
        return HasSingleValueDataSource.super.getSetValueDataSource();
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Slider", order = 2, borderColor = "#6AA427")
    @UIFieldColorPicker(allowThreshold = true)
    public String getSliderColor() {
        return getJsonData("sc", UI.Color.WHITE);
    }

    @UIField(order = 2)
    @UIFieldGroup("Slider")
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    @UIField(order = 3)
    @UIFieldNumber(min = 0)
    @UIFieldGroup("Slider")
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    @UIField(order = 4)
    @UIFieldNumber(min = 1)
    @UIFieldGroup("Slider")
    public Integer getStep() {
        return getJsonData("step", 1);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public void setSliderColor(String value) {
        setJsonData("sc", value);
    }

    public WidgetSliderSeriesEntity setMin(Integer value) {
        setJsonData("min", value);
        return this;
    }

    public WidgetSliderSeriesEntity setMax(Integer value) {
        setJsonData("max", value);
        return this;
    }

    public void setStep(Integer value) {
        setJsonData("step", value);
    }

    @Override
    protected void beforePersist() {
        HasIcon.randomColor(this);
        if (!getJsonData().has("sc")) {
            setSliderColor(UI.Color.random());
        }
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public AggregationType getAggregationType() {
        throw new ProhibitedExecution();
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getNoValueText() {
        throw new ProhibitedExecution();
    }
}
