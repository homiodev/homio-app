package org.touchhome.app.model.entity.widget.impl.display;

import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.*;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

import javax.persistence.Entity;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity>
        implements HasSingleValueDataSource, HasIcon, HasValueTemplate, HasName, HasValueConverter {

    public static final String PREFIX = "wgsdps_";

    @UIField(order = 1)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
    public String getBackground() {
        return getJsonData("bg", "transparent");
    }

    @UIField(order = 2)
    @UIFieldGroup("UI")
    public String getPadding() {
        return getJsonData("padding", "4px");
    }

    @Override
    protected void beforePersist() {
        HasIcon.randomColor(this);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public void setBackground(String value) {
        setJsonData("bg", value);
    }

    public void setPadding(String value) {
        setJsonData("padding", value);
    }
}
