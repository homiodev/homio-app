package org.homio.app.model.entity.widget.attributes;

import java.util.List;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldType;

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
