package org.homio.app.model.entity.widget.attributes;

import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasSingleValueDataSource extends HasDynamicParameterFields {

    @UIField(order = 10, required = true)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup(value = "VALUE", order = 1)
    @UIEditReloadWidget
    default String getValueDataSource() {
        return getJsonData("vds");
    }

    default void setValueDataSource(String value) {
        setJsonData("vds", value);
    }

}
