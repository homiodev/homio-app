package org.touchhome.app.model.entity.widget.impl.slider;

import org.touchhome.bundle.api.entity.widget.HasSliderSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicRequestType;

import javax.persistence.Entity;

@Entity
public class WidgetSliderSeriesEntity extends WidgetSeriesEntity<WidgetSliderEntity> {

    public static final String PREFIX = "wgssls_";

    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasSliderSeries.class)
    @UIFieldGroup("Slider")
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 2)
    @UIFieldGroup("Slider")
    @UIFieldColorPicker(allowThreshold = true)
    public String getSliderColor() {
        return getJsonData("sc", "#FFFFFF");
    }

    @UIField(order = 3)
    @UIFieldGroup("Slider")
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    @UIField(order = 4)
    @UIFieldNumber(min = 0)
    @UIFieldGroup("Slider")
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    @UIField(order = 1)
    @UIFieldIconPicker(allowEmptyIcon = true, allowThreshold = true)
    @UIFieldGroup("Icon")
    public String getIcon() {
        return getJsonData("icon", "");
    }

    @UIField(order = 1)
    @UIFieldGroup("Icon")
    @UIFieldColorPicker(allowThreshold = true)
    public String getIconColor() {
        return getJsonData("iconColor", UI.Color.WHITE);
    }

    public WidgetSliderSeriesEntity setMin(Integer value) {
        setJsonData("min", value);
        return this;
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

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public void setSliderColor(String value) {
        setJsonData("sc", value);
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    public void setIconColor(String value) {
        setJsonData("iconColor", value);
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("sc")) {
            setSliderColor(UI.Color.random());
        }
    }

    @Override
    public DynamicRequestType getDynamicRequestType(Class<? extends HasEntityIdentifier> sourceClassType) {
        return DynamicRequestType.Slider;
    }
}
