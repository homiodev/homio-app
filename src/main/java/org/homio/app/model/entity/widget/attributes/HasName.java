package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;

public interface HasName extends HasJsonData {

    @UIField(order = 1)
    @UIFieldGroup(value = "NAME", order = 3)
    @UIFieldOptionFontSize
    @UIFieldReadDefaultValue
    String getName();

    @UIField(order = 2)
    @UIFieldGroup("NAME")
    @UIFieldReadDefaultValue
    default Boolean isShowName() {
        return getJsonData("shn", Boolean.TRUE);
    }

    default void setShowName(Boolean value) {
        setJsonData("shn", value);
    }

    @UIField(order = 3)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("NAME")
    @UIFieldReadDefaultValue
    default String getNameColor() {
        return getJsonData("nc", UI.Color.WHITE);
    }

    default void setNameColor(String value) {
        setJsonData("nc", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    @UIFieldReadDefaultValue
    default double getNameFontSize() {
        return getJsonData("nfs", 1D);
    }

    default void setNameFontSize(double value) {
        setJsonData("nfs", value);
    }
}
