package org.touchhome.app.model.entity.widget.impl.toggle;

import java.util.List;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasName;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnoreParent;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

@Entity
public class WidgetToggleSeriesEntity extends WidgetSeriesEntity<WidgetToggleEntity>
    implements HasSingleValueDataSource, HasIcon, HasName {

  public static final String PREFIX = "wgttgs_";

  @Override
  @UIField(order = 1, required = true)
  @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
  @UIFieldEntityByClassSelection(HasGetStatusValue.class)
  @UIFieldIgnoreParent
  @UIFieldGroup(value = "Value", order = 2)
  public String getValueDataSource() {
    return HasSingleValueDataSource.super.getValueDataSource();
  }

  @UIField(order = 2, required = true)
  @UIFieldGroup(value = "Value")
  @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
  @UIFieldEntityByClassSelection(HasSetStatusValue.class)
  public String getSetValueDataSource() {
    return HasSingleValueDataSource.super.getSetValueDataSource();
  }

  @UIField(order = 3, isRevert = true)
  @UIFieldColorPicker
  @UIFieldGroup("UI")
  public String getColor() {
    return getJsonData("color", UI.Color.WHITE);
  }

  public WidgetToggleSeriesEntity setColor(String value) {
    setJsonData("color", value);
    return this;
  }

  @UIField(order = 1)
  @UIFieldGroup(value = "ON", order = 4)
  public String getOnName() {
    return getJsonData("onName", "On");
  }

  public void setOnName(String value) {
    setJsonData("onName", value);
  }

  /**
   * Determine to check if toggle is on compare server value with list of OnValues
   */
  @UIField(order = 2)
  @UIFieldGroup("ON")
  public List<String> getOnValues() {
    return getJsonDataList("onValues");
  }

  public void setOnValues(String value) {
    setJsonData("onValues", value);
  }

  @UIField(order = 3)
  @UIFieldGroup("ON")
  public String getPushToggleOnValue() {
    return getJsonData("onValue", "true");
  }

  public void setPushToggleOnValue(String value) {
    setJsonData("onValue", value);
  }

  @UIField(order = 1)
  @UIFieldGroup(value = "OFF", order = 4)
  public String getOffName() {
    return getJsonData("offName", "Off");
  }

  public void setOffName(String value) {
    setJsonData("offName", value);
  }

  @UIField(order = 2)
  @UIFieldGroup("OFF")
  public String getPushToggleOffValue() {
    return getJsonData("offValue", "false");
  }

  public void setPushToggleOffValue(String value) {
    setJsonData("offValue", value);
  }

  @Override
  public String getEntityPrefix() {
    return PREFIX;
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  protected void beforePersist() {
    HasIcon.randomColor(this);
    if (!getJsonData().has("color")) {
      setColor(UI.Color.random());
    }
    if (getOnValues().isEmpty()) {
      setOnValues("true~~~1");
    }
  }
}
