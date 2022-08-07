package org.touchhome.app.model.entity.widget.impl.display;

import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity> {

    public static final String PREFIX = "wgsdps_";

    @UIField(order = 14, required = true)
    @UIFieldEntityByClassSelection(HasAggregateValueFromSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15)
    public AggregationType getAggregationType() {
        return getJsonDataEnum("cvt", AggregationType.Last);
    }

    public WidgetDisplaySeriesEntity setAggregationType(AggregationType value) {
        setJsonData("cvt", value);
        return this;
    }

    @UIField(order = 20)
    @UIFieldColorPicker
    public String getForeground() {
        return getJsonData("fg", "#009688");
    }

    public WidgetDisplaySeriesEntity setForeground(String value) {
        setJsonData("fg", value);
        return this;
    }

    @UIField(order = 22)
    public String getPrepend() {
        return getJsonData("prepend");
    }

    public WidgetDisplaySeriesEntity setPrepend(String value) {
        setJsonData("prepend", value);
        return this;
    }


    @UIField(order = 23)
    public String getAppend() {
        return getJsonData("append");
    }

    public WidgetDisplaySeriesEntity setAppend(String value) {
        setJsonData("append", value);
        return this;
    }

    @UIField(order = 1)
    @UIFieldIconPicker(allowEmptyIcon = true, allowThreshold = true)
    @UIFieldGroup("Icon")
    public String getIcon() {
        return getJsonData("icon", "fas fa-adjust");
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    @UIField(order = 2)
    @UIFieldColorPicker
    @UIFieldGroup("Icon")
    public String getIconColor() {
        return getJsonData("iconColor", UI.Color.WHITE);
    }

    public void setIconColor(String value) {
        setJsonData("iconColor", value);
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("iconColor")) {
            setIconColor(UI.Color.random());
        }
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
