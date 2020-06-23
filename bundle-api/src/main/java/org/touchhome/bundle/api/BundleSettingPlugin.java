package org.touchhome.bundle.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.json.Option;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public interface BundleSettingPlugin<T> {

    default String getIcon() {
        return "";
    }

    default String getToggleIcon() {
        return getIcon();
    }

    default String getIconColor() {
        return "";
    }

    default String getDefaultValue() {
        switch (getSettingType()) {
            case Boolean:
                return Boolean.FALSE.toString();
        }
        return "";
    }

    // min/max/step (Slider)
    default String[] getAvailableValues() {
        return new String[0];
    }

    SettingType getSettingType();

    default T parseValue(EntityContext entityContext, String value) {
        switch (getSettingType()) {
            case Float:
                return (T) Float.valueOf(value);
            case Boolean:
                return (T) Boolean.valueOf(value);
            case Integer:
            case Slider:
                return (T) Integer.valueOf(value);
        }
        return (T) value;
    }

    default List<Option> loadAvailableValues(EntityContext entityContext) {
        throw new IllegalStateException("Must be implemented in sub-classes");
    }

    default boolean transientState() {
        return this.getSettingType() == SettingType.Button
                || this.getSettingType() == SettingType.Info
                || this.getSettingType() == SettingType.Event;
    }

    int order();

    default boolean isAdvanced() {
        return false;
    }

    default List<NotificationEntityJSON> buildHeaderNotificationEntity(T value, EntityContext entityContext) {
        return null;
    }

    default NotificationEntityJSON buildToastrNotificationEntity(T value, EntityContext entityContext) {
        return null;
    }

    default String writeValue(T value) {
        return value == null ? "" : value.toString();
    }

    @AllArgsConstructor
    enum GroupKey {
        dashboard("fas fa-tachometer-alt"),
        map("fas fa-map"),
        usb("fab fa-usb"),
        system("fas fa-tools");

        @Getter
        private final String icon;
    }

    enum SettingType {
        ColorPicker,
        Float,
        Boolean,
        Integer,
        SelectBox,
        SelectBoxButton,
        Slider,
        SelectBoxDynamic,
        Text,
        TextSelectBoxDynamic,
        Button,
        Info,
        Event
    }
}
