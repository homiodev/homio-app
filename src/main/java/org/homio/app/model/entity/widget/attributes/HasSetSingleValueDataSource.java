package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.homio.app.model.entity.widget.UIEditReloadWidget;

public interface HasSetSingleValueDataSource extends HasDynamicParameterFields {

    @UIField(order = 12)
    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup(value = "VALUE")
    @UIEditReloadWidget
    default String getSetValueDataSource() {
        return getJsonData("svds");
    }

    default void setSetValueDataSource(String value) {
        setJsonData("svds", value);
    }
}
