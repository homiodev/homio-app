package org.touchhome.app.model.entity.widget.attributes;

import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

public interface HasActionOnClick extends HasJsonData {

    @UIField(order = 1, label = "widget.pushValueDataSource")
    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup(value = "Action on click", order = 25, borderColor = "#71B12B")
    default String getSetValueDataSource() {
        return getJsonData("svds");
    }

    default void setSetValueDataSource(String value) {
        setJsonData("svds", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("Action on click")
    default String getValueOnClick() {
        return getJsonData("voc");
    }

    default void setValueOnClick(String value) {
        setJsonData("voc", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("Action on click")
    default String getValueOnDoubleClick() {
        return getJsonData("vodc");
    }

    default void setValueOnDoubleClick(String value) {
        setJsonData("vodc", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("Action on click")
    default String getValueOnHoldClick() {
        return getJsonData("vohc");
    }

    default void setValueOnHoldClick(String value) {
        setJsonData("vohc", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("Action on click")
    default String getValueOnHoldReleaseClick() {
        return getJsonData("vohrc");
    }

    default void setValueOnHoldReleaseClick(String value) {
        setJsonData("vohrc", value);
    }

    @UIField(order = 10)
    @UIFieldGroup("Action on click")
    default String getValueToPushConfirmMessage() {
        return getJsonData("vtpcm");
    }

    default void setValueToPushConfirmMessage(String value) {
        setJsonData("vtpcm", value);
    }

    @UIField(order = 15)
    @UIFieldSlider(min = 0, max = 1000, step = 50)
    @UIFieldGroup("Action on click")
    default int getSendValueOnHoldInterval() {
        return getJsonData("svohi", 0);
    }

    default void setSendValueOnHoldInterval(int value) {
        setJsonData("svohi", value);
    }
}
