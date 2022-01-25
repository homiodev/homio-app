package org.touchhome.app.manager.common.v1.item;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.common.v1.UIBaseEntityItemBuilderImpl;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.item.UISelectBoxItemBuilder;

import java.util.ArrayList;
import java.util.Collection;

@Getter
public class UISelectBoxItemBuilderImpl extends UIBaseEntityItemBuilderImpl<UISelectBoxItemBuilder, String>
        implements UISelectBoxItemBuilder, UIInputEntityActionHandler {

    private Collection<OptionModel> options;
    private String selectReplacer;
    private boolean asButton;
    private String text;

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
    public UISelectBoxItemBuilder setAsButton(@Nullable String icon, @Nullable String iconColor, @Nullable String text) {
        this.setIcon(icon, iconColor);
        this.text = text;
        this.asButton = true;
        return this;
    }

    @Override
    public UISelectBoxItemBuilderImpl setOptions(Collection<OptionModel> options) {
        this.options = options;
        return this;
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