package org.touchhome.app.model.entity.widget.impl.button;

import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetPushButtonSeriesEntity extends WidgetSeriesEntity<WidgetPushButtonEntity>
        implements HasChartDataSource<WidgetPushButtonEntity>, HasSingleValueDataSource<WidgetPushButtonEntity> {

    public static final String PREFIX = "wgsbs_";

    @Override
    @UIField(order = 1, required = true, label = "widget.pushValueDataSource")
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup(value = "Action Data source", order = 1)
    public String getSetValueDataSource() {
        return HasSingleValueDataSource.super.getSetValueDataSource();
    }

    @UIField(order = 2, required = true)
    @UIFieldGroup(value = "Action Data source")
    public String getValueToPush() {
        return getJsonData("valToPush");
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "UI", order = 2, borderColor = "#009688")
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
    public String getButtonColor() {
        return getJsonData("btnClr", UI.Color.WHITE);
    }

    @UIField(order = 2)
    @UIFieldGroup("UI")
    public String getConfirmMessage() {
        return getJsonData("confirm", "");
    }

    @UIField(order = 1)
    @UIFieldIconPicker(allowThreshold = true, allowEmptyIcon = true)
    @UIFieldGroup(value = "UI icon", order = 3, borderColor = "#009688")
    public String getIcon() {
        return getJsonData("icon", "");
    }

    @UIField(order = 2)
    @UIFieldGroup(value = "UI icon")
    @UIFieldPosition
    public String getIconPosition() {
        return getJsonData("iconPos", "2x3");
    }

    @UIField(order = 3)
    @UIFieldGroup("UI icon")
    @UIFieldColorPicker(allowThreshold = true)
    public String getIconColor() {
        return getJsonData("iconColor", UI.Color.WHITE);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @UIField(order = 1, type = UIFieldType.StringTemplate)
    @UIFieldGroup(value = "Value", order = 4, borderColor = "#E91E63")
    public String getValueTemplate() {
        return getJsonData("valTmpl");
    }

    @UIField(order = 2)
    @UIFieldGroup("Value")
    public String getNoValueText() {
        return getJsonData("noVal", "-");
    }

    @UIField(order = 3)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("Value")
    public String getValueColor() {
        return getJsonData("valC", UI.Color.WHITE);
    }

    @UIField(order = 2)
    @UIFieldPosition
    @UIFieldGroup("Value")
    public String getValuePosition() {
        return getJsonData("valuePos", "2x3");
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("btnClr")) {
            setButtonColor(UI.Color.random());
        }
        setInitChartColor(UI.Color.random());
    }

    public WidgetPushButtonSeriesEntity setButtonColor(String value) {
        setJsonData("btnClr", value);
        return this;
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    public void setIconColor(String value) {
        setJsonData("iconColor", value);
    }

    public void setIconPosition(String value) {
        setJsonData("iconPos", value);
    }

    public void setConfirmMessage(String value) {
        setJsonData("confirm", value);
    }

    public void setValueToPush(String value) {
        setJsonData("valToPush", value);
    }

    public void setValuePosition(String value) {
        setJsonData("valuePos", value);
    }

    public void setNoValueText(String value) {
        setJsonData("noVal", value);
    }

    public void setValueTemplate(String value) {
        setJsonData("valTmpl", value);
    }

    public void setValueColor(String value) {
        setJsonData("valC", value);
    }
}
