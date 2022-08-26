package org.touchhome.app.model.entity.widget.impl.slider;

import org.touchhome.app.model.entity.widget.UIFieldLayout;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

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
    public Boolean getShowTimeButtons() {
        return super.getShowTimeButtons();
    }

    @Override
    @UIFieldIgnore
    public TimePeriod getTimePeriod() {
        return super.getTimePeriod();
    }

    public void setVertical(Boolean value) {
        setJsonData("vt", value);
    }

    public void setThumbLabel(Boolean value) {
        setJsonData("tl", value);
    }

    @UIField(order = 50)
    @UIFieldLayout(options = {"name", "value", "icon", "slider"})
    public String getLayout() {
        return getJsonData("layout");
    }

    @Override
    protected String getDefaultLayout() {
        return UIFieldLayout.LayoutBuilder.builder().addRow(rb -> rb
                        .addCol("icon", UIFieldLayout.HorizontalAlign.center)
                        .addCol("name", UIFieldLayout.HorizontalAlign.center)
                        .addCol("slider", UIFieldLayout.HorizontalAlign.center))
                .build();
    }
}
