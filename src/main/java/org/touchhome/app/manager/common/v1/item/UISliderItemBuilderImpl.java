package org.touchhome.app.manager.common.v1.item;

import lombok.Getter;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.item.UISliderItemBuilder;

@Getter
public class UISliderItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UISliderItemBuilder, Float>
    implements UISliderItemBuilder {

  private Float min;
  private Float max;
  private Float step;
  private boolean required;

  private SliderType sliderType;
  private boolean hideThumbLabel;

  public UISliderItemBuilderImpl(String entityID, int order, UIActionHandler actionHandler, float value, float min, float max) {
    super(UIItemType.Slider, entityID, order, actionHandler);
    this.min = min;
    this.max = max;
    setValue(value);
    this.sliderType = SliderType.Regular;
  }

  public UISliderItemBuilder setRequired(boolean required) {
    this.required = required;
    return this;
  }

  @Override
  public UISliderItemBuilder setHideThumbLabel(boolean hideThumbLabel) {
    this.hideThumbLabel = hideThumbLabel;
    return this;
  }

  @Override
  public UISliderItemBuilder setSliderType(SliderType sliderType) {
    this.sliderType = sliderType;
    return this;
  }

  @Override
  public UISliderItemBuilder setStep(Float step) {
    this.step = step;
    return this;
  }
}
