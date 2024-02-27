package org.homio.app.builder.ui;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

import java.util.ArrayList;
import java.util.Collection;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.homio.api.ui.field.action.v1.item.UISelectBoxItemBuilder;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.app.rest.ItemController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Accessors(chain = true)
public class UISelectBoxItemBuilderImpl
        extends UIBaseEntityItemBuilderImpl<UISelectBoxItemBuilder, String>
        implements UISelectBoxItemBuilder, UIInputEntityActionHandler {

    @Setter
    private Collection<OptionModel> options;
    private String optionFetcher;
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
    public @NotNull UISelectBoxItemBuilderImpl setSelectReplacer(int min, int max, String selectReplacer) {
        if (StringUtils.isNotEmpty(selectReplacer)) {
            this.selectReplacer = min + LIST_DELIMITER + max + LIST_DELIMITER + selectReplacer;
        }
        return this;
    }

    @Override
    public @NotNull UIButtonItemBuilder setAsButton(@Nullable Icon icon, @Nullable String text) {
        this.setIcon(icon);
        this.text = text;
        this.asButton = true;
        return new UIButtonItemBuilderImpl(UIItemType.Button, "", null, 0, null) {
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
    public @NotNull UISelectBoxItemBuilder setLazyItemOptions(@NotNull Class<? extends BaseEntity> itemClass) {
        optionFetcher = "rest/item/type/%s/options".formatted(itemClass.getSimpleName());
        return this;
    }

    @Override
    public @NotNull UISelectBoxItemBuilder setLazyOptionLoader(@NotNull Class<? extends DynamicOptionLoader> itemClass) {
        ItemController.className2Class.put(itemClass.getSimpleName(), itemClass);
        optionFetcher = "rest/item/type/%s/options".formatted(itemClass.getSimpleName());
        return this;
    }

    @Override
    public @NotNull UISelectBoxItemBuilderImpl addOption(@NotNull OptionModel option) {
        if (options == null) {
            options = new ArrayList<>();
        }
        options.add(option);
        return this;
    }
}
