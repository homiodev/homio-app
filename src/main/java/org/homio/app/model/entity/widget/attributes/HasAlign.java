package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPosition;
import org.homio.api.ui.field.UIFieldTab;

public interface HasAlign extends HasJsonData {

  @UIField(order = 102)
  @UIFieldGroup("GENERAL")
  @UIFieldTab("UI")
  @UIFieldPosition(disableCenter = false)
  default String getAlign() {
    // default center align
    return getJsonData("al", "2x2");
  }

  default void setAlign(String value) {
    setJsonData("al", value);
  }
}
