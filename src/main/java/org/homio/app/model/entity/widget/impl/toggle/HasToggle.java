package org.homio.app.model.entity.widget.impl.toggle;

import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.attributes.HasSetSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;

import java.util.List;

public interface HasToggle extends HasSingleValueDataSource, HasSetSingleValueDataSource {

    @UIField(order = 12, required = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    default String getSetValueDataSource() {
        return HasSetSingleValueDataSource.super.getSetValueDataSource();
    }

    @UIField(order = 3)
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
