package org.homio.app.builder.ui;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import org.homio.api.model.Icon;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.v1.UIEntityItemBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public abstract class UIBaseEntityItemBuilderImpl<Owner, Value>
        implements UIEntityItemBuilder<Owner, Value>, UIInputEntity, UIInputEntityActionHandler {

    private final String itemType;
    private final UIActionHandler actionHandler;
    private final String entityID;
    private String title;
    private int order;
    private Boolean disabled;
    @JsonIgnore
    private Map<String, Runnable> fetchValueHandlers;

    private Value value;
    private String color;
    private String outerClass;

    @JsonIgnore
    private Map<String, String> styleMap;
    private String icon;
    private String iconColor;
    private String separatedText;

    public UIBaseEntityItemBuilderImpl(
            UIItemType uiItemType, String entityID, int order, UIActionHandler actionHandler) {
        this.itemType = uiItemType.name();
        this.entityID = entityID;
        this.actionHandler = actionHandler;
        this.order = order;
    }

    @Override
    public String toString() {
        return "%s{%s:%d}".formatted(getClass().getSimpleName(), getEntityID(), getOrder());
    }

    @Override
    public UIInputEntity buildEntity() {
        return this;
    }

    @Override
    public Owner setColor(String color) {
        this.color = color;
        return (Owner) this;
    }

    @Override
    public String getStyle() {
        return styleMap == null
                ? null
                : styleMap.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue() + ";")
                .collect(Collectors.joining());
    }

    @Override
    public Owner appendStyle(@NotNull String style, @NotNull String value) {
        if (this.styleMap == null) {
            this.styleMap = new HashMap<>(2);
        }
        this.styleMap.put(style, value);
        return (Owner) this;
    }

    @Override
    public Owner setOuterClass(String outerClass) {
        this.outerClass = outerClass;
        return (Owner) this;
    }

    @Override
    public Owner setTitle(String title) {
        this.title = title;
        return (Owner) this;
    }

    @Override
    public Owner setSeparatedText(String sepText) {
        this.separatedText = sepText;
        return (Owner) this;
    }

    @Override
    public Owner setValue(Value value) {
        this.value = value;
        return (Owner) this;
    }

    @Override
    public Owner setDisabled(boolean disabled) {
        this.disabled = disabled ? true : null;
        return (Owner) this;
    }

    @Override
    public Owner setOrder(int order) {
        this.order = order;
        return (Owner) this;
    }

    @Override
    public Owner setIcon(@Nullable Icon icon) {
        if (icon != null) {
            this.icon = icon.getIcon();
            this.iconColor = icon.getColor();
        }
        return (Owner) this;
    }

    @Override
    public Owner addFetchValueHandler(String key, Runnable fetchValueHandler) {
        if (fetchValueHandlers == null) {
            fetchValueHandlers = new HashMap<>();
        }
        fetchValueHandlers.put(key, fetchValueHandler);
        return (Owner) this;
    }
}
