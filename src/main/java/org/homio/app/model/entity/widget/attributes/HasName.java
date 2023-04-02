package org.homio.app.model.entity.widget.attributes;

import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.bundle.api.entity.HasJsonData;
import org.homio.bundle.api.ui.UI;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldColorPicker;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;

public interface HasName extends HasJsonData {

    @UIField(order = 1)
    @UIFieldGroup(value = "Name", order = 3)
    @UIFieldOptionFontSize
    @UIFieldReadDefaultValue
    String getName();

    @UIField(order = 2)
    @UIFieldGroup("Name")
    @UIFieldReadDefaultValue
    default Boolean isShowName() {
        return getJsonData("shn", Boolean.TRUE);
    }

    default void setShowName(Boolean value) {
        setJsonData("shn", value);
    }

    @UIField(order = 3, isRevert = true)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("Name")
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
