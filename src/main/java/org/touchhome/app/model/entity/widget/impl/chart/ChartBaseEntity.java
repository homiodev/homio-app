package org.touchhome.app.model.entity.widget.impl.chart;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.WidgetGroup;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

@Entity
public abstract class ChartBaseEntity<T extends WidgetBaseEntityAndSeries, S extends WidgetSeriesEntity<T>>
    extends WidgetBaseEntityAndSeries<T, S> implements HasLegend {

  @Override
  public WidgetGroup getGroup() {
    return WidgetGroup.Chart;
  }

  @Override
  @UIField(order = 10)
  public String getTitle() {
    return super.getName();
  }

  public void setTitle(String value) {
    super.setName(value);
  }

  @UIField(order = 1)
  @UIFieldGroup("Data labels")
  public boolean getShowDataLabels() {
    return getJsonData("sdl", false);
  }

  public void setShowDataLabels(boolean value) {
    setJsonData("sdl", value);
  }

  @UIField(order = 2)
  @UIFieldGroup("Data labels")
  @UIFieldColorPicker
  public String getDataLabelsColor() {
    return getJsonData("dlc", "#ADB5BD");
  }

  public void setDataLabelsColor(String value) {
    setJsonData("dlc", value);
  }

  @UIField(order = 100)
  public Boolean getAnimations() {
    return getJsonData("am", Boolean.FALSE);
  }

  public T setAnimations(Boolean value) {
    setJsonData("am", value);
    return (T) this;
  }
}
