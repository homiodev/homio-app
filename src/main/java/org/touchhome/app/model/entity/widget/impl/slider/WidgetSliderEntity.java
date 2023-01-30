package org.touchhome.app.model.entity.widget.impl.slider;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIFieldLayout;
import org.touchhome.app.model.entity.widget.UIFieldUpdateFontSize;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.impl.HasLayout;
import org.touchhome.app.model.entity.widget.impl.HasName;
import org.touchhome.app.model.entity.widget.impl.HasSourceServerUpdates;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

@Entity
public class WidgetSliderEntity
    extends WidgetBaseEntityAndSeries<WidgetSliderEntity, WidgetSliderSeriesEntity>
    implements HasLayout, HasSourceServerUpdates, HasName {

    public static final String PREFIX = "wgtsl_";

    @UIField(order = 1)
    @UIFieldGroup(value = "Name", order = 1)
    @UIFieldUpdateFontSize
    public String getName() {
        return super.getName();
    }

    @UIField(order = 2)
    @UIFieldGroup("Slider")
    public Boolean isVertical() {
        return getJsonData("vt", Boolean.FALSE);
    }

    @UIField(order = 3)
    @UIFieldGroup("Slider")
    public Boolean getThumbLabel() {
        return getJsonData("tl", Boolean.TRUE);
    }

    public void setThumbLabel(Boolean value) {
        setJsonData("tl", value);
    }

    @UIField(order = 6)
    @UIFieldGroup("Slider")
    public Boolean isUpdateOnMove() {
        return getJsonData("uom", Boolean.FALSE);
    }

    @Override
    public String getImage() {
        return "fas fa-sliders-h";
    }

    @Override
    @UIFieldColorPicker(allowThreshold = true)
    public String getBackground() {
        return super.getBackground();
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public void setVertical(Boolean value) {
        setJsonData("vt", value);
    }

    public void setUpdateOnMove(Boolean value) {
        setJsonData("uom", value);
    }

    @Override
    @UIField(order = 50)
    @UIFieldLayout(options = {"name", "value", "icon", "slider"})
    public String getLayout() {
        return getJsonData("layout");
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setLayout(
            UIFieldLayout.LayoutBuilder
                .builder(15, 20, 50, 15)
                .addRow(rb ->
                    rb.addCol("icon", UIFieldLayout.HorizontalAlign.center)
                      .addCol("name", UIFieldLayout.HorizontalAlign.left)
                      .addCol("slider", UIFieldLayout.HorizontalAlign.center)
                      .addCol("value", UIFieldLayout.HorizontalAlign.center))
                .build());
    }
}
