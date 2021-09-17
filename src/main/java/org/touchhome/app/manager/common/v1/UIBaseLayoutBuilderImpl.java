package org.touchhome.app.manager.common.v1;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.common.v1.item.*;
import org.touchhome.app.manager.common.v1.layout.UIDialogLayoutBuilderImpl;
import org.touchhome.app.manager.common.v1.layout.UIFlexLayoutBuilderImpl;
import org.touchhome.app.manager.common.v1.layout.UIStickyDialogItemBuilderImpl;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.UIEntityBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.item.*;
import org.touchhome.bundle.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class UIBaseLayoutBuilderImpl implements UILayoutBuilder {

    @Getter
    protected final Map<String, UIEntityBuilder> inputBuilders = new HashMap<>();
    private Map<String, String> styleMap;

    public abstract int getOrder();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getEntityID() + ":" + getOrder() + "}";
    }

    @Override
    public String getStyle() {
        return styleMap == null ? null : styleMap.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue() + ";")
                .collect(Collectors.joining());
    }

    @Override
    public UILayoutBuilder removeStyle(@NotNull String style) {
        if (this.styleMap != null) {
            this.styleMap.remove(style);
        }
        return this;
    }

    @Override
    public UILayoutBuilder appendStyle(@NotNull String style, @NotNull String value) {
        if (this.styleMap == null) {
            this.styleMap = new HashMap<>(2);
        }
        this.styleMap.put(style, value);
        return this;
    }

    @Override
    public Collection<UIEntityBuilder> getUiEntityBuilders(boolean flat) {
        if (flat) {
            Collection<UIEntityBuilder> builders = new ArrayList<>();
            for (UIEntityBuilder entityBuilder : inputBuilders.values()) {
                builders.add(entityBuilder);
                if (entityBuilder instanceof UILayoutBuilder) {
                    builders.addAll(((UILayoutBuilder) entityBuilder).getUiEntityBuilders(flat));
                } else if (entityBuilder instanceof UIButtonItemBuilder) {
                    UIStickyDialogItemBuilder stickyDialogBuilder = ((UIButtonItemBuilderImpl) entityBuilder).getStickyDialogBuilder();
                    if (stickyDialogBuilder != null) {
                        builders.addAll(stickyDialogBuilder.getUiEntityBuilders(flat));
                    }
                }
            }
            return builders;
        }
        return Collections.unmodifiableCollection(inputBuilders.values());
    }

    @Override
    public void addRawUIEntityBuilder(@NotNull String name, UIEntityBuilder source) {
        inputBuilders.put(name, source);
    }

    @Override
    public DialogEntity<UIStickyDialogItemBuilder> addStickyDialogButton(@NotNull String name, String icon, String iconColor, int order) {
        UIStickyDialogItemBuilderImpl stickyDialogBuilder;
        UIButtonItemBuilderImpl buttonItemBuilder;
        if (inputBuilders.containsKey(name)) {
            buttonItemBuilder = (UIButtonItemBuilderImpl) inputBuilders.get(name);
            stickyDialogBuilder = (UIStickyDialogItemBuilderImpl) buttonItemBuilder.getStickyDialogBuilder();
        } else {
            String entityID = getText(name);
            stickyDialogBuilder = new UIStickyDialogItemBuilderImpl(entityID + "_dialog");
            buttonItemBuilder = ((UIButtonItemBuilderImpl) addButton(entityID, icon, iconColor, null, order))
                    .setStickyDialogEntityBuilder(stickyDialogBuilder);
        }
        return new DialogEntity<UIStickyDialogItemBuilder>() {
            @Override
            public UIStickyDialogItemBuilder up() {
                return stickyDialogBuilder;
            }

            @Override
            public UIStickyDialogItemBuilder editButton(Consumer<UIButtonItemBuilder> editHandler) {
                editHandler.accept(buttonItemBuilder);
                return stickyDialogBuilder;
            }
        };
    }

    @Override
    public DialogEntity<UIDialogLayoutBuilder> addOpenDialogActionButton(@NotNull String name, String icon, String iconColor, Integer width, int order) {
        String entityID = getText(name);
        UIDialogLayoutBuilderImpl dialogEntityBuilder = new UIDialogLayoutBuilderImpl(entityID + "_dialog", width);
        UIButtonItemBuilderImpl buttonItemBuilder = ((UIButtonItemBuilderImpl) addButton(entityID, icon, iconColor, null, order))
                .setDialogEntityBuilder(dialogEntityBuilder);
        return new DialogEntity<UIDialogLayoutBuilder>() {
            @Override
            public UIDialogLayoutBuilder up() {
                return dialogEntityBuilder;
            }

            @Override
            public UIDialogLayoutBuilder editButton(Consumer<UIButtonItemBuilder> editHandler) {
                editHandler.accept(buttonItemBuilder);
                return dialogEntityBuilder;
            }
        };
    }

    @Override
    public UIFlexLayoutBuilder addFlex(@NotNull String name, int order) {
        return addEntity(new UIFlexLayoutBuilderImpl(name, order));
    }

    @Override
    public UIInfoItemBuilder addInfo(@NotNull String name, UIInfoItemBuilder.InfoType infoType, int order) {
        return addEntity(new UIInfoItemBuilderImpl(name, order, name, infoType));
    }

    @Override
    public UISelectBoxItemBuilder addSelectBox(@NotNull String name, UIActionHandler action, int order) {
        return addEntity(new UISelectBoxItemBuilderImpl(name, order, action));
    }

    @Override
    public UICheckboxItemBuilder addCheckbox(@NotNull String name, boolean value, UIActionHandler action, int order) {
        return addEntity(new UICheckboxItemBuilderImpl(name, order, action, value));
    }

    @Override
    public UIMultiButtonItemBuilder addMultiButton(@Nullable String text, UIActionHandler action, int order) {
        return addEntity(new UIMultiButtonItemBuilderImpl(getText(text), order, action));
    }

    @Override
    public UISliderItemBuilder addSlider(@NotNull String name, float value, float min, float max, UIActionHandler action,
                                         UISliderItemBuilder.SliderType sliderType, int order) {
        return addEntity(new UISliderItemBuilderImpl(name, order, action, value, min, max));
    }

    @Override
    public UIButtonItemBuilder addButton(@NotNull String name, String icon, String iconColor, UIActionHandler action, int order) {
        return addEntity(new UIButtonItemBuilderImpl(UIItemType.Button, name, icon, iconColor, order, action));
    }

    public <T extends UIEntityBuilder> T addEntity(T entityBuilder) {
        if (inputBuilders.containsKey(entityBuilder.getEntityID())) {
            return (T) inputBuilders.get(entityBuilder.getEntityID());
        }
        inputBuilders.put(entityBuilder.getEntityID(), entityBuilder);
        return entityBuilder;
    }

    public int getNextOrder() {
        return inputBuilders.size() + 1;
    }

    public String getText(String text) {
        return text == null ? "btn_" + System.currentTimeMillis() : text;
    }

    protected void from(UIInputBuilder source) {
        UIBaseLayoutBuilderImpl sourceBuilder = (UIInputBuilderImpl) source;
        this.inputBuilders.putAll(sourceBuilder.inputBuilders);
        if (sourceBuilder.styleMap != null) {
            if (this.styleMap == null) {
                this.styleMap = new HashMap<>();
            }
            this.styleMap.putAll(sourceBuilder.styleMap);
        }
    }
}
