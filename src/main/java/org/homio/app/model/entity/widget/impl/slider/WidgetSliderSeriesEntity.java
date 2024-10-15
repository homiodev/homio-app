package org.homio.app.model.entity.widget.impl.slider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.apache.commons.lang3.NotImplementedException;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.HasOptionsForEntityByClassFilter;
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
        HasTextConverter,
        HasOptionsForEntityByClassFilter {

    @UIField(order = 1)
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
        throw new NotImplementedException();
    }

    @Override
    public void beforePersist() {
        HasIcon.randomColor(this);
        if (!getJsonData().has("sc")) {
            setSliderColor(UI.Color.random());
        }
    }

    @Override
    public boolean isExclude(Class<? extends HasEntityIdentifier> sourceClassType, BaseEntity baseEntity) {
        if (baseEntity instanceof HasGetStatusValue) {
            HasGetStatusValue.ValueType valueType = ((HasGetStatusValue) baseEntity).getValueType();
            return valueType != HasGetStatusValue.ValueType.Unknown && valueType != HasGetStatusValue.ValueType.Float;
        }

        if (baseEntity instanceof HasSetStatusValue) {
            HasGetStatusValue.ValueType valueType = ((HasSetStatusValue) baseEntity).getValueType();
            return valueType != HasGetStatusValue.ValueType.Unknown && valueType != HasGetStatusValue.ValueType.Float;
        }

        return false;
    }
}
