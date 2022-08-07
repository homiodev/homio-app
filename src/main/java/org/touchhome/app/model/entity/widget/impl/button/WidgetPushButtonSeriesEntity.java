package org.touchhome.app.model.entity.widget.impl.button;

import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.bundle.api.entity.widget.HasPushButtonSeries;
import org.touchhome.bundle.api.entity.widget.HasStatusSeries;
import org.touchhome.bundle.api.entity.widget.HasTimeValueAndLastValueSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicRequestType;

import javax.persistence.Entity;

@Entity
public class WidgetPushButtonSeriesEntity extends WidgetSeriesEntity<WidgetPushButtonEntity>
        implements HasChartDataSource<WidgetPushButtonEntity> {

    public static final String PREFIX = "wgsbs_";

    @UIField(order = 1, required = true)
    @UIFieldEntityByClassSelection(HasPushButtonSeries.class)
    @UIFieldGroup(value = "Data source", order = 1)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 2)
    @UIFieldEntityByClassSelection(HasStatusSeries.class)
    @UIFieldGroup("Data source")
    public String getFetchStatusDataSource() {
        return getJsonData("fdds");
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "UI", order = 2)
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
    public String getButtonColor() {
        return getJsonData("btnClr", UI.Color.WHITE);
    }

    @UIField(order = 2)
    @UIFieldGroup("UI")
    public Boolean getShowRawValue() {
        return getJsonData("srw", Boolean.FALSE);
    }

    @UIField(order = 3)
    @UIFieldGroup("UI")
    public String getConfirmMessage() {
        return getJsonData("confirm", "");
    }

    @UIField(order = 1)
    @UIFieldIconPicker(allowThreshold = true, allowEmptyIcon = true)
    @UIFieldGroup(value = "UI icon", order = 3)
    public String getIcon() {
        return getJsonData("icon", "");
    }

    @UIField(order = 2)
    @UIFieldGroup("UI icon")
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

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("btnClr")) {
            setButtonColor(UI.Color.random());
        }
    }

    @Override
    public DynamicRequestType getDynamicRequestType(Class<? extends HasEntityIdentifier> sourceClassType) {
        if (sourceClassType.equals(HasStatusSeries.class)) {
            return DynamicRequestType.ValueStatus;
        } else if (sourceClassType.equals(HasTimeValueAndLastValueSeries.class)) {
            return DynamicRequestType.Default;
        }
        return DynamicRequestType.PushButton;
    }

    public WidgetPushButtonSeriesEntity setButtonColor(String value) {
        setJsonData("btnClr", value);
        return this;
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    public void setFetchStatusDataSource(String value) {
        setJsonData("fdds", value);
    }

    public void setShowRawValue(Boolean value) {
        setJsonData("srw", value);
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
}
