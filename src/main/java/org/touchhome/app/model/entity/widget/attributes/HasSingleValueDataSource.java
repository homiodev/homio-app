package org.touchhome.app.model.entity.widget.attributes;

import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasSingleValueDataSource extends HasDynamicParameterFields {

    @UIField(order = 10, required = true)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldGroup(value = "Value", order = 1)
    @UIEditReloadWidget
    default String getValueDataSource() {
        return getJsonData("vds");
    }

    default void setValueDataSource(String value) {
        setJsonData("vds", value);
    }

}
