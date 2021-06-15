package org.touchhome.app.model.entity.widget.impl.button;

import org.touchhome.bundle.api.entity.widget.HasPushButtonSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldClassWithFeatureSelection;

import javax.persistence.Entity;

@Entity
public class WidgetButtonSeriesEntity extends WidgetSeriesEntity<WidgetButtonEntity> {

    public static final String PREFIX = "wtbs_";

    @UIField(order = 14, required = true)
    @UIFieldClassWithFeatureSelection(HasPushButtonSeries.class)
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

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
