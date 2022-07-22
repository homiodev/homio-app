package org.touchhome.app.model.entity.widget.impl.display;

import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
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

    @UIField(order = 20, type = UIFieldType.ColorPicker)
    public String getForeground() {
        return getJsonData("fg", "#009688");
    }

    public WidgetDisplaySeriesEntity setForeground(String value) {
        setJsonData("fg", value);
        return this;
    }

    @UIField(order = 21, type = UIFieldType.ColorPicker)
    public String getBackground() {
        return getJsonData("bg", "rgba(0, 0, 0, 0.1)");
    }

    public WidgetDisplaySeriesEntity setBackground(String value) {
        setJsonData("bg", value);
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

    @UIField(order = 24)
    public boolean getShowLastUpdateDate() {
        return getJsonData("showLastUpdateDate", Boolean.FALSE);
    }

    public WidgetDisplaySeriesEntity setShowLastUpdateDate(boolean value) {
        setJsonData("showLastUpdateDate", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
