package org.homio.app.model.entity.widget.impl.toggle;

import jakarta.persistence.Entity;
import org.homio.api.ContextWidget.ToggleType;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.attributes.HasBackground;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasPadding;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetToggleEntity
        extends WidgetEntityAndSeries<WidgetToggleEntity, WidgetToggleSeriesEntity>
        implements HasBackground, HasLayout, HasPadding, HasName {

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
    @UIFieldReadDefaultValue
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff' || context.get('displayType') == 'Slide'")
    public String getLayout() {
        return getJsonData("layout", getDefaultLayout());
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "HEADER", order = 5)
    @UIFieldShowOnCondition("return context.get('displayType') == 'OnOff' || context.get('displayType') == 'Slide'")
    public Boolean getShowAllButton() {
        return getJsonData("all", Boolean.FALSE);
    }

    public void setShowAllButton(Boolean value) {
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

    @UIField(order = 100)
    @UIFieldGroup("UI")
    @UIFieldSlider(min = 0, max = 20)
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup'")
    public int getBetweenGap() {
        return getJsonData("gap", 0);
    }

    public void setBetweenGap(int value) {
        setJsonData("gap", value);
    }

    @UIField(order = 130)
    @UIFieldGroup("UI")
    @UIFieldSlider(min = 0, max = 30)
    @UIFieldShowOnCondition("return context.get('displayType') == 'SwitchGroup'")
    public int getBorderRadius() {
        return getJsonData("br", 0);
    }

    public void setBorderRadius(int value) {
        setJsonData("br", value);
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
}
