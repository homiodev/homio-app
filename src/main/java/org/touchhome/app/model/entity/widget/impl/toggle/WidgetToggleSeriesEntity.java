package org.touchhome.app.model.entity.widget.impl.toggle;

import org.touchhome.bundle.api.entity.widget.HasToggleSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldClassWithFeatureSelection;

import javax.persistence.Entity;

@Entity
public class WidgetToggleSeriesEntity extends WidgetSeriesEntity<WidgetToggleEntity> {

    public static final String PREFIX = "wttgs_";

    @UIField(order = 14, required = true)
    @UIFieldClassWithFeatureSelection(HasToggleSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15)
    public String getOnName() {
        return getJsonData("onName", "On");
    }

    public WidgetToggleSeriesEntity setOnName(String value) {
        setJsonData("onName", value);
        return this;
    }

    @UIField(order = 16)
    public String getOffName() {
        return getJsonData("offName", "Off");
    }

    public WidgetToggleSeriesEntity setOffName(String value) {
        setJsonData("offName", value);
        return this;
    }

    @UIField(order = 17, type = UIFieldType.ColorPicker)
    public String getColor() {
        return getJsonData("color", "#FFFFFF");
    }

    public WidgetToggleSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
