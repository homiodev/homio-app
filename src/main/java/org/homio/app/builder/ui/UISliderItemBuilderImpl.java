package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.bundle.api.ui.action.UIActionHandler;
import org.homio.bundle.api.ui.field.action.v1.item.UISliderItemBuilder;

@Getter
@Accessors(chain = true)
public class UISliderItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UISliderItemBuilder, Float>
        implements UISliderItemBuilder {

    private final Float min;
    private final Float max;
    @Setter
    private Float step;
    @Setter
    private boolean required;
    @Setter
    private SliderType sliderType;
    @Setter
    private boolean hideThumbLabel;

    public UISliderItemBuilderImpl(String entityID, int order, UIActionHandler actionHandler, float value, Float min, Float max) {
        super(UIItemType.Slider, entityID, order, actionHandler);
        this.min = min;
        this.max = max;
        setValue(value);
        this.sliderType = SliderType.Regular;
    }
}
