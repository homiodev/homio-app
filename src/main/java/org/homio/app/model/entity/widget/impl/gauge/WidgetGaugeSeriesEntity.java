package org.homio.app.model.entity.widget.impl.gauge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;

@Entity
public class WidgetGaugeSeriesEntity extends WidgetSeriesEntity<WidgetGaugeEntity>
        implements HasSingleValueDataSource, HasIcon, HasValueTemplate {

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected String getSeriesPrefix() {
        return "gauge";
    }

    @Override
    public void beforePersist() {
        HasIcon.randomColor(this);
    }

    @UIField(order = 1)
    public boolean getUseGaugeValue() {
        return getJsonData("ugv", Boolean.FALSE);
    }

    public void setUseGaugeValue(boolean value) {
        setJsonData("ugv", value);
    }

    @Override
    @UIField(order = 10)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup(value = "VALUE", order = 1)
    @UIEditReloadWidget
    @UIFieldShowOnCondition("return context.get('useGaugeValue') != true")
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return getJsonData("vds");
    }

    @UIField(order = 20)
    @UIFieldReadDefaultValue
    @UIFieldSlider(min = -50, max = 50)
    @UIFieldGroup("VALUE")
    public int getPosition() {
        return getJsonData("pos", 0);
    }

    public void setPosition(int value) {
        setJsonData("pos", value);
    }

    @UIField(order = 25)
    @UIFieldReadDefaultValue
    @UIFieldSlider(min = -50, max = 50)
    @UIFieldGroup("VALUE")
    public int getShift() {
        return getJsonData("shift", 0);
    }

    public void setShift(int value) {
        setJsonData("shift", value);
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getName() {
        return super.getName();
    }
}
