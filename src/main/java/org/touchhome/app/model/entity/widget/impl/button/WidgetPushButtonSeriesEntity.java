package org.touchhome.app.model.entity.widget.impl.button;

import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.HasValueTemplate;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetPushButtonSeriesEntity extends WidgetSeriesEntity<WidgetPushButtonEntity>
        implements HasChartDataSource, HasSingleValueDataSource, HasIcon, HasValueTemplate {

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
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
    public String getButtonColor() {
        return getJsonData("btnClr", UI.Color.WHITE);
    }

    @UIField(order = 2)
    @UIFieldGroup("UI")
    public String getConfirmMessage() {
        return getJsonData("confirm", "");
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
        HasChartDataSource.randomColor(this);
        HasIcon.randomColor(this);
    }

    public WidgetPushButtonSeriesEntity setButtonColor(String value) {
        setJsonData("btnClr", value);
        return this;
    }

    public void setConfirmMessage(String value) {
        setJsonData("confirm", value);
    }

    public void setValueToPush(String value) {
        setJsonData("valToPush", value);
    }
}
