package org.touchhome.app.manager.common.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.v1.UIEntityItemBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public abstract class UIBaseEntityItemBuilderImpl<Owner, Value> implements UIEntityItemBuilder<Owner, Value>,
        UIInputEntity {

    private final String itemType;
    private final UIActionHandler actionHandler;
    private final String entityID;
    private String title;
    private int order;
    private boolean disabled;
    @JsonIgnore
    private Map<String, Runnable> fetchValueHandlers;

    private Value value;
    private String color;
    private String outerClass;
    private String description;

    @JsonIgnore
    private Map<String, String> styleMap;

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getEntityID() + ":" + getOrder() + "}";
    }

    public UIBaseEntityItemBuilderImpl(UIItemType uiItemType, String entityID, int order, UIActionHandler actionHandler) {
        this.itemType = uiItemType.name();
        this.entityID = entityID;
        this.actionHandler = actionHandler;
        this.order = order;
        this.title = entityID;
    }

    private String icon;
    private String iconColor;

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
    public Owner setDescription(String description) {
        this.description = description;
        return (Owner) this;
    }

    @Override
    public String getStyle() {
        return styleMap == null ? null : styleMap.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue() + ";")
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
    public Owner setValue(Value value) {
        this.value = value;
        return (Owner) this;
    }

    @Override
    public Owner setDisabled(boolean disabled) {
        this.disabled = disabled;
        return (Owner) this;
    }

    @Override
    public Owner setOrder(int order) {
        this.order = order;
        return (Owner) this;
    }

    @Override
    public Owner setIcon(String icon, String iconColor) {
        this.icon = icon;
        this.iconColor = iconColor;
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
