package org.homio.app.model.entity.widget.attributes;

import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasSetSingleValueDataSource extends HasDynamicParameterFields {

    @UIField(order = 12)
    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup(value = "Value")
    @UIEditReloadWidget
    default String getSetValueDataSource() {
        return getJsonData("svds");
    }

    default void setSetValueDataSource(String value) {
        setJsonData("svds", value);
    }
}
