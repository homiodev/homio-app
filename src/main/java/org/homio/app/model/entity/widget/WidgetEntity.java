package org.homio.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasPermissions;
import org.homio.api.model.JSON;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.attributes.HasPosition;
import org.homio.app.model.entity.widget.attributes.HasStyle;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@UISidebarMenu(order = 1200,
        icon = "fas fa-tachometer-alt",
        bg = "#107d6b",
        overridePath = "widgets")
@Accessors(chain = true)
@NoArgsConstructor
public abstract class WidgetEntity<T extends WidgetEntity> extends BaseEntity
        implements HasPosition<WidgetEntity>, HasStyle, HasPermissions {

    private static final String PREFIX = "widget_";

    @Override
    public final @NotNull String getEntityPrefix() {
        return PREFIX + getWidgetPrefix() + "_";
    }

    protected abstract @NotNull String getWidgetPrefix();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private WidgetTabEntity widgetTabEntity;

    @Column(length = 65535)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

    /**
     * Uses for grouping widget by type on UI
     */
    public WidgetGroup getGroup() {
        return null;
    }

    public String getFieldFetchType() {
        return getJsonData("fieldFetchType", (String) null);
    }

    public T setFieldFetchType(String value) {
        jsonData.put("fieldFetchType", value);
        return (T) this;
    }

    @Override
    @UIFieldIgnore
    public String getName() {
        return super.getName();
    }

    public abstract @NotNull String getImage();

    /**
     * Is able to create widget from UI
     */
    public boolean isVisible() {
        return true;
    }

    @Override
    public @NotNull String getDynamicUpdateType() {
        return "widget";
    }

    @Override
    public void afterUpdate() {
        ((ContextImpl) context()).event().removeEvents(getEntityID());
    }

    @Override
    public void afterDelete() {
        ((ContextImpl) context()).event().removeEvents(getEntityID());
    }

    @Override
    protected long getChildEntityHashCode() {
        return 0;
    }


}
