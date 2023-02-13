package org.touchhome.app.model.entity.widget.impl.slider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasName;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.HasTextConverter;
import org.touchhome.app.model.entity.widget.impl.HasValueTemplate;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldNumber;

@Entity
public class WidgetSliderSeriesEntity extends WidgetSeriesEntity<WidgetSliderEntity>
    implements HasSingleValueDataSource, HasIcon, HasValueTemplate, HasName, HasTextConverter {

    public static final String PREFIX = "wgssls_";

    @UIField(order = 1, isRevert = true)
    @UIFieldGroup(value = "Slider", order = 2, borderColor = "#6AA427")
    @UIFieldColorPicker(allowThreshold = true)
    public String getSliderColor() {
        return getJsonData("sc", UI.Color.WHITE);
    }

    public void setSliderColor(String value) {
        setJsonData("sc", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("Slider")
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    public WidgetSliderSeriesEntity setMin(Integer value) {
        setJsonData("min", value);
        return this;
    }

    @UIField(order = 3)
    @UIFieldNumber(min = 0)
    @UIFieldGroup("Slider")
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    public WidgetSliderSeriesEntity setMax(Integer value) {
        setJsonData("max", value);
        return this;
    }

    @UIField(order = 4)
    @UIFieldNumber(min = 1)
    @UIFieldGroup("Slider")
    public Integer getStep() {
        return getJsonData("step", 1);
    }

    public void setStep(Integer value) {
        setJsonData("step", value);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getNoValueText() {
        throw new ProhibitedExecution();
    }

    @Override
    protected void beforePersist() {
        HasIcon.randomColor(this);
        if (!getJsonData().has("sc")) {
            setSliderColor(UI.Color.random());
        }
    }
}
