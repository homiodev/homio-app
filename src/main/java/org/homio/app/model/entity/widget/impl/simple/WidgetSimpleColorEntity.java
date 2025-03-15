package org.homio.app.model.entity.widget.impl.simple;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldKeyValue;
import org.homio.api.ui.field.UIFieldKeyValue.KeyValueType;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.attributes.HasAlign;
import org.homio.app.model.entity.widget.attributes.HasSetSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
public class WidgetSimpleColorEntity extends WidgetEntity<WidgetSimpleColorEntity>
  implements HasAlign, HasSingleValueDataSource, HasSetSingleValueDataSource {

  @Override
  public WidgetGroup getGroup() {
    return WidgetGroup.Simple;
  }

  @Override
  public @NotNull String getImage() {
    return "fas fa-fill";
  }

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "sim-clr";
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @UIField(order = 1)
  @UIFieldKeyValue(maxSize = 20, keyType = UIFieldType.String, valueType = UIFieldType.ColorPicker,
    defaultKey = "0", showKey = false, defaultValue = "#FFFFFF", keyValueType = KeyValueType.array)
  @UIFieldGroup(order = 10, value = "COLORS")
  public String getColors() {
    return getJsonData("colors");
  }

  public void setColors(String value) {
    setJsonData("colors", value);
  }

  @UIField(order = 4)
  @UIFieldSlider(min = 0, max = 40)
  @UIFieldGroup("COLORS")
  public int getCircleSpacing() {
    return getJsonData("space", 14);
  }

  public void setCircleSpacing(int value) {
    setJsonData("space", value);
  }

  @UIField(order = 5)
  @UIFieldSlider(min = 10, max = 40)
  @UIFieldGroup("COLORS")
  public int getCircleSize() {
    return getJsonData("size", 28);
  }

  public void setCircleSize(int value) {
    setJsonData("size", value);
  }

  @Override
  public void beforePersist() {
    setOverflow(Overflow.hidden);
    if (!getJsonData().has("colors")) {
      setColors(Stream.of("#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#00FFFF", "#FFFFFF")
        .map(color -> String.format("{\"key\":\"0\",\"value\":\"%s\"}", color))
        .collect(Collectors.joining(",", "[", "]")));
    }
    if (!getJsonData().has("size")) {
      setCircleSize(20);
    }
    if (!getJsonData().has("space")) {
      setCircleSpacing(4);
    }
  }
}
