package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIconPicker;
import org.homio.api.ui.field.UIFieldReadDefaultValue;

public interface HasIconWithoutThreshold extends HasJsonData {

    static void randomColor(HasJsonData widget) {
        String randomColor = UI.Color.random();
        if (!widget.getJsonData().has("iconColor")) {
            widget.setJsonData("iconColor", randomColor);
        }
    }

    @UIField(order = 1)
    @UIFieldIconPicker(allowEmptyIcon = true)
    @UIFieldGroup(value = "ICON", order = 20, borderColor = "#009688")
    @UIFieldReadDefaultValue
    default String getIcon() {
        return getJsonData("icon", "fas fa-adjust");
    }

    default void setIcon(String value) {
        setJsonData("icon", value);
    }

    @UIField(order = 2, isRevert = true)
    @UIFieldColorPicker
    @UIFieldGroup("ICON")
    @UIFieldReadDefaultValue
    default String getIconColor() {
        return getJsonData("iconColor", UI.Color.WHITE);
    }

    default void setIconColor(String value) {
        setJsonData("iconColor", value);
    }
}
