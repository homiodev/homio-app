package org.homio.app.model.entity.widget.impl.simple;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.ContextWidget.SimpleToggleType;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.app.model.entity.widget.UIFieldPadding;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.attributes.HasAlign;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.impl.toggle.HasToggle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Entity
public class WidgetSimpleToggleEntity extends WidgetEntity<WidgetSimpleToggleEntity>
        implements HasSingleValueDataSource, HasToggle, HasAlign, HasMargin, HasName {

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Simple;
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-toggle-on";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "sim-tgl";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @UIField(order = 32)
    public SimpleToggleType getDisplayType() {
        return getJsonDataEnum("displayType", SimpleToggleType.Slide);
    }

    public void setDisplayType(SimpleToggleType value) {
        setJsonData("displayType", value);
    }

    @UIField(order = 1)
    @UIFieldGroup("NAME")
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff'")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 1)
    @UIFieldIconPicker(allowThreshold = true, allowBackground = true)
    @UIFieldGroup(value = "ICON", order = 20, borderColor = "#009688")
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff'")
    public String getIcon() {
        return getJsonData("icon", "fas fa-adjust");
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    @UIField(order = 2)
    @UIFieldColorPicker(allowThreshold = true, pulseColorCondition = true)
    @UIFieldGroup("ICON")
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff'")
    public String getIconColor() {
        return getJsonData("iconColor", UI.Color.WHITE);
    }

    public void setIconColor(String value) {
        setJsonData("iconColor", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "BUTTON_BORDER", order = 12, borderColor = "#4B9461")
    @UIFieldSlider(min = 0, max = 4)
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff'")
    public int getButtonBorderWidth() {
        return getJsonData("bbw", 1);
    }

    public void setButtonBorderWidth(int value) {
        setJsonData("bbw", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("BUTTON_BORDER")
    @UIFieldColorPicker
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff'")
    public String getButtonBorderColor() {
        return getJsonData("bbc", UI.Color.WARNING);
    }

    public void setButtonBorderColor(String value) {
        setJsonData("bbc", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("BUTTON_BORDER")
    @UIFieldSlider(min = 0, max = 30)
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff'")
    public int getButtonBorderRadius() {
        return getJsonData("bbr", 4);
    }

    public void setButtonBorderRadius(int value) {
        setJsonData("bbr", value);
    }

    @Override
    public void beforePersist() {
        setOverflow(Overflow.hidden);
        if (!getJsonData().has("color")) {
            setToggleColor(UI.Color.random());
        }
        if (getOnValues().isEmpty()) {
            setOnValues("true%s1".formatted(LIST_DELIMITER));
        }
    }

    @Override
    @UIFieldGroup("ON_OFF")
    public String getPushToggleOffValue() {
        return HasToggle.super.getPushToggleOffValue();
    }

    @Override
    @UIFieldGroup("ON_OFF")
    public List<String> getOnValues() {
        return HasToggle.super.getOnValues();
    }

    @Override
    @UIFieldGroup("ON_OFF")
    public String getPushToggleOnValue() {
        return HasToggle.super.getPushToggleOnValue();
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getOffName() {
        return HasToggle.super.getOffName();
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getOnName() {
        return HasToggle.super.getOnName();
    }

    @UIField(order = 100)
    @UIFieldPadding
    @UIFieldGroup("UI")
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff'")
    public String getPadding() {
        return getJsonData("padding", "{}");
    }

    public void setPadding(String value) {
        setJsonData("padding", value);
    }
}
