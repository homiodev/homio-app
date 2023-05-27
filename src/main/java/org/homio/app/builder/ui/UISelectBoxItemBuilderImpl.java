package org.homio.app.builder.ui;

import java.util.ArrayList;
import java.util.Collection;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.action.UIActionHandler;
import org.homio.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.homio.api.ui.field.action.v1.item.UISelectBoxItemBuilder;
import org.jetbrains.annotations.Nullable;

@Getter
@Accessors(chain = true)
public class UISelectBoxItemBuilderImpl
    extends UIBaseEntityItemBuilderImpl<UISelectBoxItemBuilder, String>
    implements UISelectBoxItemBuilder, UIInputEntityActionHandler {

    @Setter
    private Collection<OptionModel> options;
    private String selectReplacer;
    private boolean asButton;
    private boolean primary;
    private int height = 32;
    private String text;
    @Setter
    private String placeholder;
    @Setter
    private boolean highlightSelected;

    public UISelectBoxItemBuilderImpl(String entityID, int order, UIActionHandler actionHandler) {
        super(UIItemType.SelectBox, entityID, order, actionHandler);
    }

    @Override
    public UISelectBoxItemBuilderImpl setSelectReplacer(int min, int max, String selectReplacer) {
        if (StringUtils.isNotEmpty(selectReplacer)) {
            this.selectReplacer = min + "~~~" + max + "~~~" + selectReplacer;
        }
        return this;
    }

    @Override
    public UIButtonItemBuilder setAsButton(@Nullable String icon, @Nullable String iconColor, @Nullable String text) {
        this.setIcon(icon, iconColor);
        this.text = text;
        this.asButton = true;
        return new UIButtonItemBuilderImpl(UIItemType.Button, null, null, null, 0, null) {
            @Override
            public UIButtonItemBuilderImpl setPrimary(boolean value) {
                primary = value;
                return this;
            }

            @Override
            public UIButtonItemBuilderImpl setHeight(int value) {
                height = value;
                return this;
            }
        };
    }

    @Override
    public UISelectBoxItemBuilderImpl addOption(OptionModel option) {
        if (options == null) {
            options = new ArrayList<>();
        }
        options.add(option);
        return this;
    }
}
