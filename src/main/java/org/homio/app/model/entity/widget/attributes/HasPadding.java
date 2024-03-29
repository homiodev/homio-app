package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.app.model.entity.widget.UIFieldPadding;

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
