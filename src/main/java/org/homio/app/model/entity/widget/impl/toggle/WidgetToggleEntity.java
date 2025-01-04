package org.homio.app.model.entity.widget.impl.toggle;

import jakarta.persistence.Entity;
import org.homio.api.ContextWidget.ToggleType;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetToggleEntity
        extends WidgetEntityAndSeries<WidgetToggleEntity, WidgetToggleSeriesEntity>
        implements HasLayout, HasMargin, HasName {

    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff' || context.get('displayType') == 'Slide'")
    public String getName() {
        return super.getName();
    }

    @UIFieldColorPicker
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff' || context.get('displayType') == 'Slide'")
    public String getNameColor() {
        return getJsonData("nc", UI.Color.WHITE);
    }

    @Override
    @UIField(order = 50)
    @UIFieldLayout(options = {"name", "value", "icon", "button"})
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff' || context.get('displayType') == 'Slide'")
    public String getLayout() {
        return getJsonData("layout", getDefaultLayout());
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "HEADER", order = 5)
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff' || context.get('displayType') == 'Slide'")
    public boolean getShowAllButton() {
        return getJsonData("all", Boolean.FALSE);
    }

    public void setShowAllButton(boolean value) {
        setJsonData("all", value);
    }

    @UIField(order = 32)
    public ToggleType getDisplayType() {
        return getJsonDataEnum("displayType", ToggleType.Slide);
    }

    public WidgetToggleEntity setDisplayType(ToggleType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 17)
    @UIFieldGroup("UI")
    @UIFieldColorPicker
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup'")
    public String getGroupForeground() {
        return getJsonData("ac", UI.Color.PRIMARY_COLOR);
    }

    public void setGroupForeground(String value) {
        setJsonData("ac", value);
    }

    @UIField(order = 50)
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup'")
    public Boolean getAllowActiveClick() {
        return getJsonData("aac", Boolean.FALSE);
    }

    public void setAllowActiveClick(Boolean value) {
        setJsonData("aac", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "LAYOUT", order = 10, borderColor = "#4B8494")
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup'")
    public Boolean getButtonFullWidth() {
        return getJsonData("bfw", Boolean.FALSE);
    }

    public void setButtonFullWidth(Boolean value) {
        setJsonData("bfw", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("LAYOUT")
    @UIFieldSlider(min = 24, max = 100)
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup' && !context.get('buttonFullWidth')")
    public int getButtonMinWidth() {
        return getJsonData("bmw", 32);
    }

    public void setButtonMinWidth(int value) {
        setJsonData("bmw", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("LAYOUT")
    @UIFieldSlider(min = 0, max = 20)
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup' && context.get('buttonFullWidth')")
    public int getBetweenGap() {
        return getJsonData("gap", 0);
    }

    public void setBetweenGap(int value) {
        setJsonData("gap", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "BUTTON_BORDER", order = 12, borderColor = "#4B9461")
    @UIFieldSlider(min = 0, max = 4)
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup'")
    public int getButtonBorderWidth() {
        return getJsonData("bbw", 0);
    }

    public void setButtonBorderWidth(int value) {
        setJsonData("bbw", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("BUTTON_BORDER")
    @UIFieldColorPicker
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup'")
    public String getButtonBorderColor() {
        return getJsonData("bbc", UI.Color.WARNING);
    }

    public void setButtonBorderColor(String value) {
        setJsonData("bbc", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("BUTTON_BORDER")
    @UIFieldSlider(min = 0, max = 30)
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup'")
    public int getButtonBorderRadius() {
        return getJsonData("bbr", 10);
    }

    public void setButtonBorderRadius(int value) {
        setJsonData("bbr", value);
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-toggle-on";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "toggle";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    private String getDefaultLayout() {
        return UIFieldLayout.LayoutBuilder
                .builder(10, 60, 30)
                .addRow(rb ->
                        rb.addCol("icon", UIFieldLayout.HorizontalAlign.right)
                                .addCol("name", UIFieldLayout.HorizontalAlign.left)
                                .addCol("button", UIFieldLayout.HorizontalAlign.center))
                .build();
    }

    @Override
    public void beforePersist() {
        setOverflow(Overflow.hidden);
    }
}
