package org.touchhome.app.model.entity.widget.attributes;

import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldPosition;

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
