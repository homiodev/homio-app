package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPosition;

public interface HasAlign extends HasJsonData {

    @UIField(order = 102)
    @UIFieldGroup("UI")
    @UIFieldPosition(disableCenter = false)
    default String getAlign() {
        return getJsonData("al");
    }

    default void setAlign(String value) {
        setJsonData("al", value);
    }
}
