package org.touchhome.app.model.entity.widget.attributes;

import org.touchhome.app.model.entity.widget.UIFieldPadding;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

public interface HasPadding extends HasJsonData {

    @UIField(order = 101)
    @UIFieldPadding
    @UIFieldGroup("UI")
    default String getPadding() {
        return getJsonData("pd", "{}");
    }

    default void setPadding(String value) {
        setJsonData("pd", value);
    }
}
