package org.touchhome.app.model.entity.widget.impl;

import java.util.List;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldType;

public interface HasStyle extends HasJsonData {

    @UIField(order = 500, type = UIFieldType.Chips)
    @UIFieldGroup("UI")
    default List<String> getStyle() {
        return getJsonDataList("style");
    }

    default void setStyle(String value) {
        setJsonData("style", value);
    }
}
