package org.touchhome.app.model.entity.widget.impl.toggle;

import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Entity
public class WidgetToggleEntity extends WidgetBaseEntityAndSeries<WidgetToggleEntity, WidgetToggleSeriesEntity> {

    public static final String PREFIX = "wttg_";

    @UIField(order = 32)
    public ToggleType getDisplayType() {
        return getJsonDataEnum("displayType", ToggleType.Slide);
    }

    public WidgetToggleEntity setDisplayType(ToggleType value) {
        setJsonData("displayType", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-toggle-on";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    enum ToggleType {
        Regular, Slide
    }
}
