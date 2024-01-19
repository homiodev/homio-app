package org.homio.app.builder.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.item.UISliderItemBuilder;

@Getter
@Accessors(chain = true)
public class UISliderItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UISliderItemBuilder, Float>
        implements UISliderItemBuilder {

    private final Float min;
    private final Float max;

    private @Setter Float step;
    private @Setter SliderType sliderType;
    private @Setter boolean hideThumbLabel;
    private @Setter String thumbLabel;
    private @Setter Float defaultValue;

    public UISliderItemBuilderImpl(String entityID, int order, UIActionHandler actionHandler, float value, Float min, Float max) {
        super(UIItemType.Slider, entityID, order, actionHandler);
        this.min = min;
        this.max = max;
        setValue(value);
        this.sliderType = SliderType.Regular;
    }
}
