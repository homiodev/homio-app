package org.touchhome.app.model.entity.widget.impl.slider;

import org.touchhome.app.model.entity.widget.HorizontalPosition;
import org.touchhome.app.model.entity.widget.VerticalPosition;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.Entity;

@Entity
public class WidgetSliderEntity extends WidgetBaseEntityAndSeries<WidgetSliderEntity, WidgetSliderSeriesEntity> {

    public static final String PREFIX = "wgtsl_";

    @UIField(order = 31, showInContextMenu = true)
    public Boolean isVertical() {
        return getJsonData("vt", Boolean.FALSE);
    }

    public WidgetSliderEntity setVertical(Boolean value) {
        setJsonData("vt", value);
        return this;
    }

    @UIField(order = 33, showInContextMenu = true)
    public Boolean getShowValue() {
        return getJsonData("sv", Boolean.TRUE);
    }

    public WidgetSliderEntity setShowValue(Boolean value) {
        setJsonData("sv", value);
        return this;
    }

    @UIField(order = 34)
    public Boolean getThumbLabel() {
        return getJsonData("tl", Boolean.TRUE);
    }

    public WidgetSliderEntity setThumbLabel(Boolean value) {
        setJsonData("tl", value);
        return this;
    }

    @UIField(order = 35, type = UIFieldType.ColorPicker)
    public String getLabelColor() {
        return getJsonData("lc", UI.Color.PRIMARY_COLOR);
    }

    public WidgetSliderEntity setLabelColor(String value) {
        setJsonData("lc", value);
        return this;
    }

    @UIField(order = 40)
    public VerticalPosition getVerticalPosition() {
        return getJsonDataEnum("vp", VerticalPosition.Bottom);
    }

    public WidgetSliderEntity setVerticalPosition(VerticalPosition value) {
        setJsonData("vp", value);
        return this;
    }

    @UIField(order = 41)
    public HorizontalPosition getHorizontalPosition() {
        return getJsonDataEnum("hp", HorizontalPosition.Right);
    }

    public WidgetSliderEntity setHorizontalPosition(HorizontalPosition value) {
        setJsonData("hp", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-sliders-h";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
