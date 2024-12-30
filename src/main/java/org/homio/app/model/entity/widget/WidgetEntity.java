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
import org.homio.api.ui.field.*;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.attributes.HasPosition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
        implements HasPosition<WidgetEntity>, HasPermissions {

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

    @UIField(order = 500, type = UIFieldType.Chips)
    @UIFieldGroup("GENERAL")
    @UIFieldTab(value = "UI", order = 2, color = "#257180")
    public List<String> getStyle() {
        return getJsonDataList("style");
    }

    public void setStyle(String value) {
        setJsonData("style", value);
    }

    @UIField(order = 491)
    @UIFieldTab("UI")
    @UIFieldGroup("GENERAL")
    @UIFieldColorPicker
    public String getWidgetBorderColor() {
        return getJsonData("border_c");
    }

    public void setWidgetBorderColor(String value) {
        setJsonData("border_c", value);
    }

    @UIField(order = 492)
    @UIFieldTab("UI")
    @UIFieldGroup("GENERAL")
    @UIFieldSlider(min = 0, max = 10)
    public int getWidgetBorderWidth() {
        return getJsonData("border_w", 1);
    }

    public void setWidgetBorderWidth(int value) {
        setJsonData("border_w", value);
    }

    @UIField(order = 493)
    @UIFieldTab("UI")
    @UIFieldGroup("GENERAL")
    @UIFieldSlider(min = 0, max = 30)
    public int getWidgetBorderRadius() {
        return getJsonData("border_r", 4);
    }

    public void setWidgetBorderRadius(int value) {
        setJsonData("border_r", value);
    }

    @UIField(order = 21)
    @UIFieldTab("UI")
    @UIFieldGroup("GENERAL")
    @UIFieldColorPicker(allowThreshold = true, pulseColorCondition = true, thresholdSource = true)
    @UIFieldReadDefaultValue
    public String getBackground() {
        return getJsonData("bg", "transparent");
    }

    public void setBackground(String value) {
        setJsonData("bg", value);
    }

    @UIField(order = 25)
    @UIFieldTab("UI")
    @UIFieldGroup("GENERAL")
    @UIFieldSlider(min = 15, max = 25)
    public int getIndex() {
        return getJsonData("zi", 20);
    }

    public void setIndex(Integer value) {
        if (value == null || value == 20) {
            value = null;
        }
        setJsonData("zi", value);
    }

    @UIField(order = 1000)
    @UIFieldTab("UI")
    @UIFieldGroup("GENERAL")
    public boolean isAdjustFontSize() {
        return getJsonData("adjfs", Boolean.FALSE);
    }

    public void setAdjustFontSize(boolean value) {
        setJsonData("adjfs", value);
    }


    public Overflow getOverflow() {
        return getJsonDataEnum("overflow", Overflow.auto);
    }

    public void setOverflow(Overflow value) {
        setJsonDataEnum("overflow", value);
    }

    public enum Overflow {
        auto,
        hidden
    }
}
