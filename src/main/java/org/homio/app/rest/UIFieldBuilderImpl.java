package org.homio.app.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.homio.api.model.OptionModel;
import org.homio.api.model.UpdatableValue;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.HasDynamicUIFields.FieldBuilder;
import org.homio.api.ui.field.action.HasDynamicUIFields.UIFieldBuilder;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.utils.UIFieldSelectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.utils.UIFieldUtils.nullIfFalse;
import static org.homio.app.utils.UIFieldUtils.putIfNonEmpty;

@Getter
public class UIFieldBuilderImpl implements UIFieldBuilder {

    private final Map<String, FieldBuilderImpl> fields = new HashMap<>();

    @Override
    public @NotNull HasDynamicUIFields.FieldBuilder addSwitch(int order, @NotNull UpdatableValue<Boolean> value) {
        return addField(value.getName(), new FieldBuilderImpl(order, value, UIFieldType.Boolean));
    }

    @Override
    public @NotNull HasDynamicUIFields.FieldBuilder addInput(int order, @NotNull UpdatableValue<String> value) {
        throw new NotImplementedException();
    }

    @Override
    public @NotNull HasDynamicUIFields.FieldBuilder addSlider(int order, float min, float max, @Nullable String header,
                                                              @NotNull UpdatableValue<Float> value) {
        FieldBuilderImpl builder = new FieldBuilderImpl(order, value, UIFieldType.Slider);
        builder.jsonTypeMetadata.put("min", min);
        builder.jsonTypeMetadata.put("max", max);
        return addField(value.getName(), builder);
    }

    @Override
    public @NotNull HasDynamicUIFields.FieldBuilder addNumber(int order, @NotNull UpdatableValue<Integer> value) {
        return addField(value.getName(), new FieldBuilderImpl(order, value, UIFieldType.Float));
    }

    @Override
    public @NotNull HasDynamicUIFields.FieldBuilder addSelect(int order, @NotNull UpdatableValue<String> value,
                                                              @NotNull List<OptionModel> selections) {
        return addSelectBox(order, UIFieldType.SelectBox, value, selections);
    }

    @NotNull
    @Override
    public FieldBuilder addChips(int order, @NotNull UpdatableValue<String> value) {
        return addField(value.getName(), new FieldBuilderImpl(order, value, UIFieldType.Chips));
    }

    @NotNull
    @Override
    public FieldBuilder addMultiSelect(int order, @NotNull UpdatableValue<String> value, @NotNull List<OptionModel> selections) {
        return addSelectBox(order, UIFieldType.MultiSelectBox, value, selections);
    }

    private @NotNull FieldBuilder addSelectBox(int order, @NotNull UIFieldType uiFieldType, @NotNull UpdatableValue<String> value, @NotNull List<OptionModel> selections) {
        FieldBuilderImpl builder = new FieldBuilderImpl(order, value, uiFieldType);
        ObjectNode meta = UIFieldSelectionUtil.getSelectBoxList(builder.jsonTypeMetadata);
        meta.put("selectType", "inline");
        meta.set("selectOptions", OBJECT_MAPPER.valueToTree(selections));
        return addField(value.getName(), builder);
    }

    private FieldBuilder addField(String key, FieldBuilderImpl fieldBuilder) {
        fields.put(key, fieldBuilder);
        return fieldBuilder;
    }

    @RequiredArgsConstructor
    public static class FieldBuilderImpl implements FieldBuilder {

        private final EntityUIMetaData data = new EntityUIMetaData();
        private final ObjectNode jsonTypeMetadata = OBJECT_MAPPER.createObjectNode();
        private final int order;
        @Getter
        private final UpdatableValue value;
        private final UIFieldType type;

        public EntityUIMetaData build() {
            data.setOrder(order);
            data.setType(type.name());
            data.setEntityName(value.getName());
            data.setDefaultValue(value.getValue());
            jsonTypeMetadata.put("value", value.getValue().toString());
            data.setTypeMetaData(jsonTypeMetadata.toString());
            return data;
        }

        @Override
        public @NotNull FieldBuilder group(@NotNull String name, int order, @Nullable String borderColor) {
            jsonTypeMetadata.put("group", "GROUP." + name);
            jsonTypeMetadata.put("groupOrder", order);
            putIfNonEmpty(jsonTypeMetadata, "bg", borderColor);
            return this;
        }

        @Override
        public @NotNull FieldBuilder hideInEdit(boolean value) {
            data.setHideInEdit(nullIfFalse(value));
            return this;
        }

        @Override
        public @NotNull FieldBuilder label(@Nullable String value) {
            data.setLabel(trimToNull(value));
            return this;
        }

        @Override
        public @NotNull FieldBuilder hideInView(boolean value) {
            data.setHideInView(nullIfFalse(value));
            return this;
        }

        @Override
        public @NotNull FieldBuilder hideOnEmpty(boolean value) {
            data.setHideOnEmpty(nullIfFalse(value));
            return this;
        }

        @Override
        public @NotNull FieldBuilder inlineEdit(boolean value) {
            data.setInlineEdit(nullIfFalse(value));
            return this;
        }

        @Override
        public @NotNull FieldBuilder disableEdit(boolean value) {
            data.setDisableEdit(nullIfFalse(value));
            return this;
        }

        @Override
        public @NotNull FieldBuilder required(boolean value) {
            data.setRequired(nullIfFalse(value));
            return this;
        }

        @Override
        public @NotNull FieldBuilder color(@Nullable String value) {
            data.setColor(trimToNull(value));
            return this;
        }

        @Override
        public @NotNull FieldBuilder background(@Nullable String value) {
            putIfNonEmpty(jsonTypeMetadata, "bg", value);
            return this;
        }
    }
}
