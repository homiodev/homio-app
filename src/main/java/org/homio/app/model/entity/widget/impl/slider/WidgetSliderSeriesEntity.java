package org.homio.app.model.entity.widget.impl.slider;

import jakarta.persistence.Entity;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldNumber;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.app.model.entity.widget.HasOptionsForEntityByClassFilter;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasSetSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;

@Entity
public class WidgetSliderSeriesEntity
  extends WidgetSeriesEntity<WidgetSliderEntity>
  implements HasSingleValueDataSource,
  HasSetSingleValueDataSource,
  HasIcon,
  HasValueTemplate,
  HasName,
  HasMargin,
  HasValueConverter,
  HasOptionsForEntityByClassFilter {

  @UIField(order = 1)
  @UIFieldGroup(order = 2, value = "SLIDER", borderColor = "#6AA427")
  @UIFieldColorPicker(allowThreshold = true)
  public String getSliderColor() {
    return getJsonData("sc", UI.Color.WHITE);
  }

  public void setSliderColor(String value) {
    setJsonData("sc", value);
  }

  @UIField(order = 2)
  @UIFieldGroup("SLIDER")
  public Integer getMin() {
    return getJsonData("min", 0);
  }

  public WidgetSliderSeriesEntity setMin(Integer value) {
    setJsonData("min", value);
    return this;
  }

  @UIField(order = 3)
  @UIFieldNumber(min = 0)
  @UIFieldGroup("SLIDER")
  public Integer getMax() {
    return getJsonData("max", 255);
  }

  public WidgetSliderSeriesEntity setMax(Integer value) {
    setJsonData("max", value);
    return this;
  }

  @UIField(order = 4)
  @UIFieldNumber(min = 1)
  @UIFieldGroup("SLIDER")
  public Integer getStep() {
    return getJsonData("step", 1);
  }

  public void setStep(Integer value) {
    setJsonData("step", value);
  }

  @UIField(order = 5)
  @UIFieldGroup("SLIDER")
  public SliderView getSliderView() {
    return getJsonDataEnum("view", SliderView.WideSlider);
  }

  public void setSliderView(SliderView value) {
    setJsonDataEnum("view", value);
  }

  @UIField(order = 1)
  @UIFieldShowOnCondition("return context.get('sliderView') == 'WideSlider'")
  @UIFieldGroup(value = "BORDER", order = 12, borderColor = "#299942")
  @UIFieldSlider(min = 0, max = 25)
  public int getWidgetBorderRadius() {
    return getJsonData("br", 10);
  }

  public WidgetSliderSeriesEntity setWidgetBorderRadius(int value) {
    setJsonData("br", value);
    return this;
  }

  @UIField(order = 2)
  @UIFieldShowOnCondition("return context.get('sliderView') == 'WideSlider'")
  @UIFieldGroup("BORDER")
  @UIFieldSlider(min = 0, max = 10)
  public Integer getWidgetBorderWidth() {
    return getJsonData("bw", 0);
  }

  public WidgetSliderSeriesEntity setWidgetBorderWidth(Integer value) {
    setJsonData("bw", value);
    return this;
  }

  @UIField(order = 3)
  @UIFieldShowOnCondition("return context.get('sliderView') == 'WideSlider'")
  @UIFieldGroup("BORDER")
  @UIFieldColorPicker
  public String getWidgetBorderColor() {
    return getJsonData("bc");
  }

  public void setWidgetBorderColor(String value) {
    setJsonData("bc", value);
  }

  @Override
  protected String getSeriesPrefix() {
    return "slider";
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  public void beforePersist() {
    HasIcon.randomColor(this);
    if (!getJsonData().has("sc")) {
      setSliderColor(UI.Color.random());
    }
  }

  @Override
  public boolean isExclude(Class<? extends HasEntityIdentifier> sourceClassType, BaseEntity baseEntity) {
    if (baseEntity instanceof HasGetStatusValue) {
      HasGetStatusValue.ValueType valueType = ((HasGetStatusValue) baseEntity).getValueType();
      return valueType != HasGetStatusValue.ValueType.Unknown && valueType != HasGetStatusValue.ValueType.Float;
    }

    if (baseEntity instanceof HasSetStatusValue) {
      HasGetStatusValue.ValueType valueType = ((HasSetStatusValue) baseEntity).getValueType();
      return valueType != HasGetStatusValue.ValueType.Unknown && valueType != HasGetStatusValue.ValueType.Float;
    }

    return false;
  }

  enum SliderView {
    Regular, WideSlider
  }
}
