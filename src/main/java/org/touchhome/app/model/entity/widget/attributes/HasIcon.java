package org.touchhome.app.model.entity.widget.attributes;

import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIconPicker;
import org.touchhome.bundle.api.ui.field.UIFieldReadDefaultValue;

public interface HasIcon extends HasJsonData {

    static void randomColor(HasJsonData widget) {
        String randomColor = UI.Color.random();
        if (!widget.getJsonData().has("iconColor")) {
            widget.setJsonData("iconColor", randomColor);
        }
    }

    @UIField(order = 1)
    @UIFieldIconPicker(allowEmptyIcon = true, allowThreshold = true, allowBackground = true)
    @UIFieldGroup(value = "Icon", order = 20, borderColor = "#009688")
    default String getIcon() {
        return getJsonData("icon", "fas fa-adjust");
    }

    default void setIcon(String value) {
        setJsonData("icon", value);
    }

    @UIField(order = 2, isRevert = true)
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldGroup("Icon")
    @UIFieldReadDefaultValue
    default String getIconColor() {
        return getJsonData("iconColor", UI.Color.WHITE);
    }

    default void setIconColor(String value) {
        setJsonData("iconColor", value);
    }
}
