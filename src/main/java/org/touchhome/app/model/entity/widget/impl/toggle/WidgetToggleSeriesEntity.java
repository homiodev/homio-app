package org.touchhome.app.model.entity.widget.impl.toggle;

import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;
import java.util.List;

@Entity
public class WidgetToggleSeriesEntity extends WidgetSeriesEntity<WidgetToggleEntity>
        implements HasSingleValueDataSource, HasIcon {

    public static final String PREFIX = "wgttgs_";

    @Override
    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldIgnoreParent
    @UIFieldGroup(value = "Value", order = 2)
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    @UIField(order = 2, required = true)
    @UIFieldGroup(value = "Value")
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    public String getSetValueDataSource() {
        return HasSingleValueDataSource.super.getSetValueDataSource();
    }

    @UIField(order = 3)
    @UIFieldColorPicker
    @UIFieldGroup("UI")
    public String getColor() {
        return getJsonData("color", UI.Color.WHITE);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "ON", order = 4)
    public String getOnName() {
        return getJsonData("onName", "On");
    }

    /**
     * Determine to check if toggle is on compare server value with list of OnValues
     */
    @UIField(order = 2, type = UIFieldType.Chips)
    @UIFieldGroup("ON")
    public List<String> getOnValues() {
        return getJsonDataList("onValues");
    }

    @UIField(order = 3)
    @UIFieldGroup("ON")
    public String getPushToggleOnValue() {
        return getJsonData("onValue", "true");
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "OFF", order = 4)
    public String getOffName() {
        return getJsonData("offName", "Off");
    }

    @UIField(order = 2)
    @UIFieldGroup("OFF")
    public String getPushToggleOffValue() {
        return getJsonData("offValue", "false");
    }

    public void setOnName(String value) {
        setJsonData("onName", value);
    }

    public void setOffName(String value) {
        setJsonData("offName", value);
    }

    public void setOnValues(String value) {
        setJsonData("onValues", value);
    }

    public void setPushToggleOnValue(String value) {
        setJsonData("onValue", value);
    }

    public void setPushToggleOffValue(String value) {
        setJsonData("offValue", value);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public WidgetToggleSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @Override
    protected void beforePersist() {
        HasIcon.randomColor(this);
        if (!getJsonData().has("color")) {
            setColor(UI.Color.random());
        }
        if (getOnValues().isEmpty()) {
            setOnValues("true~~~1");
        }
    }
}
