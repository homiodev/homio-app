package org.touchhome.app.model.entity.widget.impl;

import org.touchhome.app.model.entity.widget.UIFieldUpdateFontSize;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

public interface HasName extends HasJsonData {

  @UIField(order = 1)
  @UIFieldGroup(value = "Name", order = 1)
  @UIFieldUpdateFontSize
  String getName();

  @UIField(order = 2)
  @UIFieldColorPicker(allowThreshold = true)
  @UIFieldGroup(value = "Name")
  default String getNameColor() {
    return getJsonData("nc", UI.Color.WHITE);
  }

  default void setNameColor(String value) {
    setJsonData("nc", value);
  }

  @UIField(order = 0, visible = false)
  default double getNameFontSize() {
    return getJsonData("nfs", 1D);
  }

  default void setNameFontSize(double value) {
    setJsonData("nfs", value);
  }
}