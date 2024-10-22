package org.homio.app.model.entity.widget.impl.slider;

import jakarta.persistence.Entity;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasPadding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Entity
public class WidgetButtonsEntity
        extends WidgetEntityAndSeries<WidgetButtonsEntity, WidgetButtonsSeriesEntity> {

    @UIField(order = 2)
    public Boolean isVertical() {
        return getJsonData("vt", Boolean.FALSE);
    }

    public void setVertical(Boolean value) {
        setJsonData("vt", value);
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "buttons";
    }

    @Override
    public @NotNull String getImage() {
        return "far fa-circle-dot";
    }

    @Override
    public @Nullable String getDefaultName() {
        return "Buttons";
    }

    @UIField(order = 12)
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getActiveColor() {
        return getJsonData("actClr", "#777777");
    }

    public void setActiveColor(String value) {
        setJsonData("actClr", value);
    }

    @UIField(order = 12)
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getBackColor() {
        return getJsonData("bcClr", "#3D3D3D");
    }

    public void setBackColor(String value) {
        setJsonData("bcClr", value);
    }
}
