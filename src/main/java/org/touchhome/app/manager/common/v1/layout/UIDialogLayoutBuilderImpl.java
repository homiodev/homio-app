package org.touchhome.app.manager.common.v1.layout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.touchhome.app.manager.common.v1.UIDialogInputEntity;
import org.touchhome.app.manager.common.v1.UIItemType;
import org.touchhome.app.manager.common.v1.item.UICheckboxItemBuilderImpl;
import org.touchhome.app.manager.common.v1.item.UIInfoItemBuilderImpl;
import org.touchhome.app.manager.common.v1.item.UISliderItemBuilderImpl;
import org.touchhome.app.manager.common.v1.item.UITextInputItemBuilderImpl;
import org.touchhome.bundle.api.ui.field.action.UIActionInput;
import org.touchhome.bundle.api.ui.field.action.v1.UIEntityBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.action.v1.item.UICheckboxItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.item.UIInfoItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.item.UISliderItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.item.UITextInputItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.layout.dialog.UIDialogLayoutBuilder;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.ui.field.action.ActionInputParameter.NAME_PATTERN;

@Getter
@Accessors(chain = true)
public class UIDialogLayoutBuilderImpl implements UIDialogLayoutBuilder {

    @Getter
    private final String entityID;

    private final Integer width;
    private final Map<String, UIEntityBuilder> inputBuilders = new LinkedHashMap<>();

    private String title;
    private String icon;
    private String iconColor;

    // this order only for flex
    @Setter
    private int order;

    @JsonIgnore
    private Map<String, String> styleMap;

    public UIDialogLayoutBuilderImpl(String entityID, Integer width) {
        this.entityID = entityID + "_dialog";
        this.title = entityID + "_title";
        this.width = width;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getEntityID() + ":" + getOrder() + "}";
    }

    @Override
    public UIDialogLayoutBuilder setTitle(String title, String icon, String iconColor) {
        if (title != null) {
            this.title = title;
        }
        this.icon = icon;
        this.iconColor = iconColor;
        return this;
    }

    @Override
    public DialogEntity<UIDialogLayoutBuilder> addFlex(@NotNull String name) {
        return addEntity(name, new UIDialogLayoutBuilderImpl(name, null).setOrder(nextOrder()));
    }

    @Override
    public String getStyle() {
        return styleMap == null ? null : styleMap.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue() + ";")
                .collect(Collectors.joining());
    }

    @Override
    public UIDialogLayoutBuilder appendStyle(@NotNull String style, @NotNull String value) {
        if (this.styleMap == null) {
            this.styleMap = new HashMap<>(2);
        }
        this.styleMap.put(style, value);
        return this;
    }

    @Override
    public DialogEntity<UISliderItemBuilder> addSlider(@NotNull String name, float value, float min, float max) {
        return addEntity(name, new UISliderItemBuilderImpl(name, nextOrder(), null, value, min, max));
    }

    public DialogEntity<UITextInputItemBuilder> addInput(@NotNull String name, String defaultValue,
                                                         UITextInputItemBuilder.InputType inputType,
                                                         boolean required) {
        return addEntity(name, new UITextInputItemBuilderImpl(name, nextOrder(), defaultValue, inputType)
                .setRequired(required));
    }

    @Override
    public DialogEntity<UICheckboxItemBuilder> addCheckbox(@NotNull String name, boolean defaultValue) {
        return addEntity(name, new UICheckboxItemBuilderImpl(name, nextOrder(), null, defaultValue));
    }

    @Override
    public DialogEntity<UIInfoItemBuilder> addInfo(@NotNull String value, UIInfoItemBuilder.InfoType infoType) {
        return addEntity(value, new UIInfoItemBuilderImpl("txt_" + value.hashCode(), nextOrder(), value, infoType));
    }

    public <T extends UIEntityBuilder> DialogEntity<T> addEntity(String key, T entityBuilder) {
        if (!NAME_PATTERN.matcher(entityBuilder.getEntityID()).matches()) {
            throw new IllegalArgumentException("Wrong name pattern for: " + entityBuilder.getEntityID());
        }
        inputBuilders.put(key, entityBuilder);
        return new DialogEntity<T>() {
            @Override
            public UIDialogLayoutBuilder up() {
                return UIDialogLayoutBuilderImpl.this;
            }

            @Override
            public UIDialogLayoutBuilder edit(Consumer<T> editHandler) {
                editHandler.accept(entityBuilder);
                return UIDialogLayoutBuilderImpl.this;
            }
        };
    }

    public int nextOrder() {
        return inputBuilders.size() + 1;
    }

    public void addInput(UIActionInput input) {
        switch (input.type()) {
            case text:
                addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.Text, input.required());
                break;
            case json:
                addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.JSON, input.required());
                break;
            case textarea:
                addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.TextArea, input.required());
                break;
            case password:
                addInput(input.name(), input.value(), UITextInputItemBuilder.InputType.Password, input.required());
                break;
            case number:
                addSlider(input.name(), Float.parseFloat(input.value()), (float) input.min(), (float) input.max())
                        .edit(sliderBuilder ->
                                sliderBuilder.setSliderType(UISliderItemBuilder.SliderType.Input).setRequired(input.required()));
                break;
            case info:
                addInfo(input.value());
                break;
            case bool:
                addCheckbox(input.name(), Boolean.parseBoolean(input.value()));
                break;
        }
    }

    public UIInputEntity buildEntity() {
        List<UIInputEntity> entities = getInputBuilders().values().stream().map(UIEntityBuilder::buildEntity)
                .sorted(Comparator.comparingInt(UIInputEntity::getOrder)).collect(Collectors.toList());
        return new UIDialogInputEntity(entityID, this.order, UIItemType.Dialog.name(), title, icon, iconColor, getStyle(), width,
                entities);
    }
}
