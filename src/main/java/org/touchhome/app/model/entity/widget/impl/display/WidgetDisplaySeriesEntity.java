package org.touchhome.app.model.entity.widget.impl.display;

import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;

import javax.persistence.Entity;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity>
        implements HasSingleValueDataSource<WidgetDisplayEntity> {

    public static final String PREFIX = "wgsdps_";

    @UIField(order = 1)
    @UIFieldGroup(value = "Text", order = 1)
    public String getName() {
        return super.getName();
    }

    @UIField(order = 2)
    @UIFieldColorPicker
    @UIFieldGroup(value = "Text")
    public String getTextColor() {
        return getJsonData("tc", UI.Color.WHITE);
    }

    @UIField(order = 1, type = UIFieldType.StringTemplate)
    @UIFieldGroup(value = "Value", order = 2)
    public String getValueTemplate() {
        return getJsonData("vt");
    }

    @UIField(order = 2)
    @UIFieldGroup("Value")
    public String getNoValueText() {
        return getJsonData("noVal", "-");
    }

    @UIField(order = 3)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("Value")
    public String getValueColor() {
        return getJsonData("vc", UI.Color.WHITE);
    }

    @UIField(order = 1)
    @UIFieldIconPicker(allowEmptyIcon = true, allowThreshold = true)
    @UIFieldGroup(value = "Icon", order = 3)
    public String getIcon() {
        return getJsonData("icon", "fas fa-adjust");
    }

    @UIField(order = 2)
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
    @UIFieldGroup("Icon")
    public String getIconColor() {
        return getJsonData("iconColor", UI.Color.WHITE);
    }

    @Override
    protected void beforePersist() {
        String randomColor = UI.Color.random();
        if (!getJsonData().has("iconColor")) {
            setIconColor(randomColor);
        }
        if (!getJsonData().has("vc")) {
            setValueColor(randomColor);
        }
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public void setIconColor(String value) {
        setJsonData("iconColor", value);
    }

    public void setTextColor(String value) {
        setJsonData("tc", value);
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

    public void setIcon(String value) {
        setJsonData("icon", value);
    }
}
