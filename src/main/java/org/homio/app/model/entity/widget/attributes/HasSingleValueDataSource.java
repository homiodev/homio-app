package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.homio.app.model.entity.widget.UIEditReloadWidget;

public interface HasSingleValueDataSource extends HasDynamicParameterFields, HasEntityIdentifier {

  @UIField(order = 10, required = true)
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
