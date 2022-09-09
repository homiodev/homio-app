package org.touchhome.app.model.entity.widget.impl.display;

import org.touchhome.app.model.entity.widget.UIFieldUpdateFontSize;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.HasValueTemplate;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

import javax.persistence.Entity;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity>
        implements HasSingleValueDataSource, HasIcon, HasValueTemplate {

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

    @UIField(order = 1)
    @UIFieldGroup(value = "Name", order = 1)
    @UIFieldUpdateFontSize
    public String getName() {
        return super.getName();
    }

    @UIField(order = 0, visible = false)
    public double getNameFontSize() {
        return getJsonData("nfs", 1D);
    }

    public void setNameFontSize(double value) {
        setJsonData("nfs", value);
    }

    @UIField(order = 2)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup(value = "Name")
    public String getNameColor() {
        return getJsonData("nc", UI.Color.WHITE);
    }

    @Override
    protected void beforePersist() {
        HasIcon.randomColor(this);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public void setNameColor(String value) {
        setJsonData("nc", value);
    }

    public void setBackground(String value) {
        setJsonData("bg", value);
    }

    public void setPadding(String value) {
        setJsonData("padding", value);
    }
}
