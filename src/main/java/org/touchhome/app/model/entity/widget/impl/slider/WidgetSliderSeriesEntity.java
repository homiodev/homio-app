package org.touchhome.app.model.entity.widget.impl.slider;

import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetSliderSeriesEntity extends WidgetSeriesEntity<WidgetSliderEntity>
        implements HasSingleValueDataSource, HasIcon {

    public static final String PREFIX = "wgssls_";

    @Override
    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup(value = "Value", order = 1)
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    @Override
    @UIField(order = 2, required = true)
    @UIFieldGroup(value = "Value")
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    public String getSetValueDataSource() {
        return HasSingleValueDataSource.super.getSetValueDataSource();
    }

    @UIField(order = 3, type = UIFieldType.StringTemplate)
    @UIFieldGroup(value = "Value")
    public String getValueTemplate() {
        return getJsonData("valTmpl");
    }

    @UIField(order = 4)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("Value")
    public String getValueColor() {
        return getJsonData("valC", UI.Color.WHITE);
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

    public void setValueTemplate(String value) {
        setJsonData("valTmpl", value);
    }

    public void setValueColor(String value) {
        setJsonData("valC", value);
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
        if (!getJsonData().has("sc")) {
            setSliderColor(UI.Color.random());
        }
    }
}
