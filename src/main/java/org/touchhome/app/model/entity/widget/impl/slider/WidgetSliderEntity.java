package org.touchhome.app.model.entity.widget.impl.slider;

import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;

import javax.persistence.Entity;

@Entity
public class WidgetSliderEntity extends WidgetBaseEntityAndSeries<WidgetSliderEntity, WidgetSliderSeriesEntity> {

    public static final String PREFIX = "wgtsl_";

    @UIField(order = 2, showInContextMenu = true)
    @UIFieldGroup("Slider")
    public Boolean isVertical() {
        return getJsonData("vt", Boolean.FALSE);
    }

    @UIField(order = 3)
    @UIFieldGroup("Slider")
    public Boolean getThumbLabel() {
        return getJsonData("tl", Boolean.TRUE);
    }

    @UIField(order = 1, showInContextMenu = true)
    @UIFieldGroup("Value label")
    public Boolean getShowValue() {
        return getJsonData("sv", Boolean.TRUE);
    }

    @UIField(order = 2)
    @UIFieldGroup("Value label")
    @UIFieldColorPicker(allowThreshold = true)
    public String getValueColor() {
        return getJsonData("lc", UI.Color.WHITE);
    }

    @UIField(order = 3)
    @UIFieldGroup("Value label")
    @UIFieldPosition
    public String getValuePosition() {
        return getJsonData("vlp", "1x1");
    }

    @Override
    public String getImage() {
        return "fas fa-sliders-h";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    @UIFieldIgnore
    public String getBackground() {
        return super.getBackground();
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowTimeButtons() {
        return super.getShowTimeButtons();
    }

    @Override
    @UIFieldIgnore
    public TimePeriod getTimePeriod() {
        return super.getTimePeriod();
    }

    public void setValueColor(String value) {
        setJsonData("lc", value);
    }

    public void setVertical(Boolean value) {
        setJsonData("vt", value);
    }

    public void setShowValue(Boolean value) {
        setJsonData("sv", value);
    }

    public void setThumbLabel(Boolean value) {
        setJsonData("tl", value);
    }

    public void setValuePosition(String value) {
        setJsonData("vlp", value);
    }
}
