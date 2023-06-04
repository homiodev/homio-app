package org.homio.app.builder.ui;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import org.homio.api.model.Icon;
import org.homio.api.ui.action.UIActionHandler;
import org.homio.api.ui.field.action.v1.UIEntityBuilder;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.item.UIButtonItemBuilder;
import org.homio.api.ui.field.action.v1.item.UICheckboxItemBuilder;
import org.homio.api.ui.field.action.v1.item.UIColorPickerItemBuilder;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder;
import org.homio.api.ui.field.action.v1.item.UIMultiButtonItemBuilder;
import org.homio.api.ui.field.action.v1.item.UISelectBoxItemBuilder;
import org.homio.api.ui.field.action.v1.item.UISliderItemBuilder;
import org.homio.api.ui.field.action.v1.item.UITextInputItemBuilder;
import org.homio.api.ui.field.action.v1.item.UITextInputItemBuilder.InputType;
import org.homio.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.UILayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;
import org.homio.api.ui.field.action.v1.layout.dialog.UIStickyDialogItemBuilder;
import org.homio.app.builder.ui.layout.UIDialogLayoutBuilderImpl;
import org.homio.app.builder.ui.layout.UIFlexLayoutBuilderImpl;
import org.homio.app.builder.ui.layout.UIStickyDialogItemBuilderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

public abstract class UIBaseLayoutBuilderImpl implements UILayoutBuilder {

    @Getter @JsonIgnore
    protected final Map<String, UIEntityBuilder> inputBuilders = new HashMap<>();

    @JsonIgnore private Map<String, String> styleMap;

    public abstract int getOrder();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getEntityID() + ":" + getOrder() + "}";
    }

    @Override
    public String getStyle() {
        return styleMap == null ? null : styleMap.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue() + ";").collect(Collectors.joining());
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
        if (!flat) {
            return Collections.unmodifiableCollection(inputBuilders.values());
        }
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

    @Override
    public void addRawUIEntityBuilder(@NotNull String name, UIEntityBuilder source) {
        inputBuilders.put(name, source);
    }

    @Override
    public DialogEntity<UIStickyDialogItemBuilder> addStickyDialogButton(@NotNull String name, Icon icon, int order) {
        UIStickyDialogItemBuilderImpl stickyDialogBuilder;
        UIButtonItemBuilderImpl buttonItemBuilder;
        if (inputBuilders.containsKey(name)) {
            buttonItemBuilder = (UIButtonItemBuilderImpl) inputBuilders.get(name);
            stickyDialogBuilder = (UIStickyDialogItemBuilderImpl) buttonItemBuilder.getStickyDialogBuilder();
        } else {
            String entityID = getText(name);
            stickyDialogBuilder = new UIStickyDialogItemBuilderImpl(entityID + "_dialog");
            buttonItemBuilder = ((UIButtonItemBuilderImpl) addButton(entityID, icon, null, order))
                .setStickyDialogBuilder(stickyDialogBuilder);
        }
        return new DialogEntity<>() {
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
    public DialogEntity<UIDialogLayoutBuilder> addOpenDialogActionButton(@NotNull String name, Icon icon, Integer width, int order) {
        String entityID = getText(name);
        UIDialogLayoutBuilderImpl dialogEntityBuilder = new UIDialogLayoutBuilderImpl(entityID, width);
        UIButtonItemBuilderImpl buttonItemBuilder = ((UIButtonItemBuilderImpl) addButton(entityID, icon, null, order))
            .setDialogEntityBuilder(dialogEntityBuilder);
        return new DialogEntity<>() {
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
    public UITextInputItemBuilder addInput(@NotNull String name, String defaultValue, InputType inputType, boolean required) {
        return addEntity(new UITextInputItemBuilderImpl(name, getNextOrder(), defaultValue, inputType)
            .setRequired(required));
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
    public UISliderItemBuilder addSlider(@NotNull String name, Float value, Float min, Float max, UIActionHandler action,
        UISliderItemBuilder.SliderType sliderType, int order) {
        return addEntity(
            new UISliderItemBuilderImpl(name, order, action, value, min, max)
                .setSliderType(sliderType));
    }

    @Override
    public UIButtonItemBuilder addButton(@NotNull String name, Icon icon, UIActionHandler action, int order) {
        return addEntity(new UIButtonItemBuilderImpl(UIItemType.Button, name, icon, order, action));
    }

    @Override
    public UIButtonItemBuilder addTableLayoutButton(@NotNull String name, int maxRows, int maxColumns, String value, @Nullable Icon icon,
        UIActionHandler action, int order) {
        return addEntity(new UIButtonItemBuilderImpl(UIItemType.TableLayout, name, icon == null ? new Icon("fas fa-table") : icon, order, action)
            .setMetadata(new JSONObject().put("maxRows", maxRows).put("maxColumns", maxColumns).put("value", value)));
    }

    @Override
    public UIButtonItemBuilder addSimpleUploadButton(@NotNull String name, @Nullable Icon icon, String[] supportedFormats,
        UIActionHandler action, int order) {
        return addEntity(new UIButtonItemBuilderImpl(UIItemType.SimpleUploadButton, name, icon, order, action)
            .setMetadata(new JSONObject().put("supportedFormats", supportedFormats)));
    }

    @Override
    public UIColorPickerItemBuilder addColorPicker(@NotNull String name, String color, UIActionHandler action) {
        return addEntity(new UIColorPickerBuilderImpl(name, getNextOrder(), color, action));
    }

    @Override
    public void addDuration(long value, @Nullable String color) {
        addEntity(new UIDurationBuilderImpl(System.currentTimeMillis() + "", getNextOrder(), value, color));
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
        if (sourceBuilder != null) {
            this.inputBuilders.putAll(sourceBuilder.inputBuilders);
            if (sourceBuilder.styleMap != null) {
                if (this.styleMap == null) {
                    this.styleMap = new HashMap<>();
                }
                this.styleMap.putAll(sourceBuilder.styleMap);
            }
        }
    }
}
