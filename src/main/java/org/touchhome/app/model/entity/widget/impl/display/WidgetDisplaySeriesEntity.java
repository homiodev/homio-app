package org.touchhome.app.model.entity.widget.impl.display;

import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;

import javax.persistence.Entity;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity>
        implements HasSingleValueDataSource, HasIcon {

    public static final String PREFIX = "wgsdps_";

    @UIField(order = 1)
    @UIFieldGroup(value = "UI", order = 10, borderColor = "#009688")
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
    public String getBackground() {
        return getJsonData("bg", "transparent");
    }

    @UIField(order = 2)
    @UIFieldGroup(value = "UI")
    public String getPadding() {
        return getJsonData("padding", "4px");
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Name", order = 1)
    public String getName() {
        return super.getName();
    }

    @UIField(order = 2)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup(value = "Name")
    public String getNameColor() {
        return getJsonData("nc", UI.Color.WHITE);
    }

    @UIField(order = 1, type = UIFieldType.StringTemplate)
    @UIFieldGroup(value = "Value", order = 20)
    public String getValueTemplate() {
        return getJsonData("vt", "~~~℃");
    }

    @UIField(order = 21)
    @UIFieldGroup("Value")
    public String getNoValueText() {
        return getJsonData("noVal", "-");
    }

    @UIField(order = 22)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("Value")
    public String getValueColor() {
        return getJsonData("vc", UI.Color.WHITE);
    }

    @Override
    protected void beforePersist() {
        HasIcon.randomIcon(this);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public void setNameColor(String value) {
        setJsonData("nc", value);
    }

    public void setNoValueText(String value) {
        setJsonData("noVal", value);
    }

    public void setValueTemplate(String value) {
        setJsonData("vt", value);
    }

    public void setValueColor(String value) {
        setJsonData("vc", value);
    }

    public void setBackground(String value) {
        setJsonData("bg", value);
    }

    public void setPadding(String value) {
        setJsonData("padding", value);
    }
}
