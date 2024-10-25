package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.app.model.entity.widget.UIFieldPadding;

public interface HasMargin extends HasJsonData {

    @UIField(order = 10)
    @UIFieldPadding
    @UIFieldGroup("GENERAL")
    @UIFieldTab("UI")
    default String getMargin() {
        return getJsonData("margin", "{}");
    }

    default void setMargin(String value) {
        setJsonData("margin", value);
    }
}
