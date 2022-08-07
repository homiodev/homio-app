package org.touchhome.app.model.entity.widget.impl.toggle;

import org.touchhome.bundle.api.entity.widget.HasToggleSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicRequestType;

import javax.persistence.Entity;

@Entity
public class WidgetToggleSeriesEntity extends WidgetSeriesEntity<WidgetToggleEntity> {

    public static final String PREFIX = "wgttgs_";

    @UIField(order = 14, required = true)
    @UIFieldEntityByClassSelection(HasToggleSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 20)
    @UIFieldColorPicker
    public String getColor() {
        return getJsonData("color", UI.Color.WHITE);
    }

    public WidgetToggleSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "ON", order = 1)
    public String getOnName() {
        return getJsonData("onName", "On");
    }

    @UIField(order = 2)
    @UIFieldGroup("ON")
    public String getOnValue() {
        return getJsonData("onValue", "true");
    }

    @UIField(order = 4)
    @UIFieldIconPicker(allowEmptyIcon = true)
    @UIFieldGroup("ON")
    public String getOnIcon() {
        return getJsonData("onIcon", "");
    }

    @UIField(order = 5)
    @UIFieldColorPicker
    @UIFieldGroup("ON")
    public String getOnIconColor() {
        return getJsonData("onIconColor", UI.Color.WHITE);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "OFF", order = 2)
    public String getOffName() {
        return getJsonData("offName", "Off");
    }

    @UIField(order = 2)
    @UIFieldGroup("OFF")
    public String getOffValue() {
        return getJsonData("offValue", "false");
    }

    @UIField(order = 4)
    @UIFieldIconPicker(allowEmptyIcon = true)
    @UIFieldGroup("OFF")
    public String getOffIcon() {
        return getJsonData("offIcon", "");
    }

    @UIField(order = 5)
    @UIFieldColorPicker
    @UIFieldGroup("OFF")
    public String getOffIconColor() {
        return getJsonData("offIconColor", UI.Color.WHITE);
    }

    public void setOnName(String value) {
        setJsonData("onName", value);
    }

    public void setOffName(String value) {
        setJsonData("offName", value);
    }

    public void setOnValue(String value) {
        setJsonData("onValue", value);
    }

    public void setOffValue(String value) {
        setJsonData("offValue", value);
    }

    public void setOnIcon(String value) {
        setJsonData("onIcon", value);
    }

    public void setOnIconColor(String value) {
        setJsonData("onIconColor", value);
    }

    public void setOffIcon(String value) {
        setJsonData("offIcon", value);
    }

    public void setOffIconColor(String value) {
        setJsonData("offIconColor", value);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("color")) {
            setColor(UI.Color.random());
        }
    }

    @Override
    public DynamicRequestType getDynamicRequestType(Class<? extends HasEntityIdentifier> sourceClassType) {
        return DynamicRequestType.Toggle;
    }
}
