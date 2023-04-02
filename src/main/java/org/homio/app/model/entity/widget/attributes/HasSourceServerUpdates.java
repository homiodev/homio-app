package org.homio.app.model.entity.widget.attributes;

import org.homio.bundle.api.entity.HasJsonData;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;

public interface HasSourceServerUpdates extends HasJsonData {

    @UIField(order = 2)
    @UIFieldGroup(value = "Update", order = 1000)
    @UIFieldReadDefaultValue
    default Boolean getListenSourceUpdates() {
        return getJsonData("lsu", Boolean.TRUE);
    }

    default void setListenSourceUpdates(Boolean value) {
        setJsonData("lsu", value);
    }

    @UIField(order = 10)
    @UIFieldGroup("Update")
    @UIFieldReadDefaultValue
    default Boolean getShowLastUpdateTimer() {
        return getJsonData("slut", Boolean.FALSE);
    }

    default void setShowLastUpdateTimer(Boolean value) {
        setJsonData("slut", value);
    }
}
