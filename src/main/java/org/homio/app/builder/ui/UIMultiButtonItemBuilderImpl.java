package org.homio.app.builder.ui;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.homio.api.model.Icon;
import org.homio.api.ui.action.UIActionHandler;
import org.homio.api.ui.field.action.v1.item.UIMultiButtonItemBuilder;
import org.jetbrains.annotations.NotNull;

@Getter
public class UIMultiButtonItemBuilderImpl
        extends UIBaseEntityItemBuilderImpl<UIMultiButtonItemBuilder, String>
        implements UIMultiButtonItemBuilder {

    private final List<ExtraButton> buttons = new ArrayList<>();

    public UIMultiButtonItemBuilderImpl(String entityID, int order, UIActionHandler actionHandler) {
        super(UIItemType.MultiButton, entityID, order, actionHandler);
    }

    @Override
    public @NotNull UIMultiButtonItemBuilderImpl addButton(@NotNull String title) {
        buttons.add(new ExtraButton(title, null, null));
        return this;
    }

    @Override
    public @NotNull UIMultiButtonItemBuilderImpl addButton(@NotNull String title, @NotNull Icon icon) {
        buttons.add(new ExtraButton(title, icon.getColor(), icon.getIcon()));
        return this;
    }

    @Override
    public @NotNull UIMultiButtonItemBuilderImpl setActive(@NotNull String activeButton) {
        setValue(activeButton);
        return this;
    }

    @SuppressWarnings("unused")
    public List<ExtraButton> getButtons() {
        ArrayList<ExtraButton> list = new ArrayList<>();
        list.add(new ExtraButton(getEntityID(), getIconColor(), getIcon()));
        list.addAll(buttons);
        return list;
    }

    @Override
    public String getIcon() {
        return null;
    }

    @Override
    public String getIconColor() {
        return null;
    }

    @Getter
    @AllArgsConstructor
    public static class ExtraButton {

        String name;
        String iconColor;
        String icon;
    }
}
