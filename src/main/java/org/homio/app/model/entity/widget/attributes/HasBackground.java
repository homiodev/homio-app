package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.*;

public interface HasBackground extends HasJsonData {

    @UIField(order = 21)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true, pulseColorCondition = true, thresholdSource = true)
    @UIFieldReadDefaultValue
    default String getBackground() {
        return getJsonData("bg", "transparent");
    }

    default void setBackground(String value) {
        setJsonData("bg", value);
    }

    @UIField(order = 25)
    @UIFieldGroup("UI")
    @UIFieldSlider(min = 15, max = 25)
    default int getIndex() {
        return getJsonData("zi", 20);
    }

    default void setIndex(Integer value) {
        if (value == null || value == 20) {
            value = null;
        }
        setJsonData("zi", value);
    }

    @UIField(order = 1000)
    @UIFieldGroup(order = 10, value = "UI", borderColor = "#009688")
    default boolean isAdjustFontSize() {
        return getJsonData("adjfs", Boolean.FALSE);
    }

    default void setAdjustFontSize(boolean value) {
        setJsonData("adjfs", value);
    }
}
