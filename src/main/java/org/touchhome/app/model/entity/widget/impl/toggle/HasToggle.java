package org.touchhome.app.model.entity.widget.impl.toggle;

import java.util.List;
import org.touchhome.app.model.entity.widget.attributes.HasSetSingleValueDataSource;
import org.touchhome.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

public interface HasToggle extends HasSingleValueDataSource, HasSetSingleValueDataSource {

    @UIField(order = 12, required = true)
    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    default String getSetValueDataSource() {
        return HasSetSingleValueDataSource.super.getSetValueDataSource();
    }

    @UIField(order = 3, isRevert = true)
    @UIFieldColorPicker
    @UIFieldGroup("UI")
    @UIFieldReadDefaultValue
    default String getColor() {
        return getJsonData("color", UI.Color.WHITE);
    }

    default void setColor(String value) {
        setJsonData("color", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "ON", order = 4)
    default String getOnName() {
        return getJsonData("onName", "On");
    }

    default void setOnName(String value) {
        setJsonData("onName", value);
    }

    /**
     * Determine to check if toggle is on compare server value with list of OnValues
     */
    @UIField(order = 2)
    @UIFieldGroup("ON")
    default List<String> getOnValues() {
        return getJsonDataList("onValues");
    }

    default void setOnValues(String value) {
        setJsonData("onValues", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("ON")
    default String getPushToggleOnValue() {
        return getJsonData("onValue", "true");
    }

    default void setPushToggleOnValue(String value) {
        setJsonData("onValue", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "OFF", order = 4)
    default String getOffName() {
        return getJsonData("offName", "Off");
    }

    default void setOffName(String value) {
        setJsonData("offName", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("OFF")
    default String getPushToggleOffValue() {
        return getJsonData("offValue", "false");
    }

    default void setPushToggleOffValue(String value) {
        setJsonData("offValue", value);
    }
}
