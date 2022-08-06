package org.touchhome.app.model.entity.widget.impl.button;

import org.touchhome.bundle.api.entity.widget.HasPushButtonSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIconPicker;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetButtonSeriesEntity extends WidgetSeriesEntity<WidgetButtonEntity> {

    public static final String PREFIX = "wgsbs_";

    @UIField(order = 14, required = true)
    @UIFieldEntityByClassSelection(HasPushButtonSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 17, type = UIFieldType.ColorPicker)
    public String getColor() {
        return getJsonData("color", "#FFFFFF");
    }

    public WidgetButtonSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @UIField(order = 20)
    @UIFieldIconPicker
    public String getIcon() {
        return getJsonData("icon", "");
    }

    public WidgetButtonSeriesEntity setIcon(String value) {
        setJsonData("icon", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
