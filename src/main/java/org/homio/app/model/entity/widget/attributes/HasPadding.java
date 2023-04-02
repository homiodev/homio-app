package org.homio.app.model.entity.widget.attributes;

import org.homio.app.model.entity.widget.UIFieldPadding;
import org.homio.bundle.api.entity.HasJsonData;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;

public interface HasPadding extends HasJsonData {

    @UIField(order = 101)
    @UIFieldPadding
    @UIFieldGroup("UI")
    @UIFieldReadDefaultValue
    default String getPadding() {
        return getJsonData("pd", "{}");
    }

    default void setPadding(String value) {
        setJsonData("pd", value);
    }
}
