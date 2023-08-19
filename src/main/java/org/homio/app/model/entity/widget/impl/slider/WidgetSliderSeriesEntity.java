package org.homio.app.model.entity.widget.impl.slider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.exception.ProhibitedExecution;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.*;

@Entity
public class WidgetSliderSeriesEntity
        extends WidgetSeriesEntity<WidgetSliderEntity>
        implements HasSingleValueDataSource,
        HasSetSingleValueDataSource,
        HasIcon,
        HasValueTemplate,
        HasName,
        HasPadding,
        HasTextConverter {

    @UIField(order = 1, isRevert = true)
    @UIFieldGroup(order = 2, value = "SLIDER", borderColor = "#6AA427")
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldReadDefaultValue
    public String getSliderColor() {
        return getJsonData("sc", UI.Color.WHITE);
    }

    public void setSliderColor(String value) {
        setJsonData("sc", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("SLIDER")
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    public WidgetSliderSeriesEntity setMin(Integer value) {
        setJsonData("min", value);
        return this;
    }

    @UIField(order = 3)
    @UIFieldNumber(min = 0)
    @UIFieldGroup("SLIDER")
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    public WidgetSliderSeriesEntity setMax(Integer value) {
        setJsonData("max", value);
        return this;
    }

    @UIField(order = 4)
    @UIFieldNumber(min = 1)
    @UIFieldGroup("SLIDER")
    public Integer getStep() {
        return getJsonData("step", 1);
    }

    public void setStep(Integer value) {
        setJsonData("step", value);
    }

    @Override
    protected String getSeriesPrefix() {
        return "slider";
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
