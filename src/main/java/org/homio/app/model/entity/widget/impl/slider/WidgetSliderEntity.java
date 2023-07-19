package org.homio.app.model.entity.widget.impl.slider;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldLayout;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasPadding;
import org.homio.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetSliderEntity
    extends WidgetBaseEntityAndSeries<WidgetSliderEntity, WidgetSliderSeriesEntity>
    implements HasLayout, HasSourceServerUpdates, HasName, HasPadding {

    public static final String PREFIX = "wgtsl_";

    @UIField(order = 1)
    @UIFieldGroup(value = "NAME", order = 3)
    @UIFieldOptionFontSize
    public String getName() {
        return super.getName();
    }

    @UIField(order = 2)
    @UIFieldGroup("SLIDER")
    public Boolean isVertical() {
        return getJsonData("vt", Boolean.FALSE);
    }

    @UIField(order = 3)
    @UIFieldGroup("SLIDER")
    public Boolean getThumbLabel() {
        return getJsonData("tl", Boolean.TRUE);
    }

    public void setThumbLabel(Boolean value) {
        setJsonData("tl", value);
    }

    @UIField(order = 6)
    @UIFieldGroup("SLIDER")
    public Boolean isUpdateOnMove() {
        return getJsonData("uom", Boolean.FALSE);
    }

    @Override
    public @NotNull String getImage() {
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
        return getJsonData("layout", getDefaultLayout());
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    private String getDefaultLayout() {
        return UIFieldLayout.LayoutBuilder
            .builder(15, 20, 50, 15)
            .addRow(rb ->
                rb.addCol("icon", UIFieldLayout.HorizontalAlign.center)
                  .addCol("name", UIFieldLayout.HorizontalAlign.left)
                  .addCol("slider", UIFieldLayout.HorizontalAlign.center)
                  .addCol("value", UIFieldLayout.HorizontalAlign.center))
            .build();
    }
}
