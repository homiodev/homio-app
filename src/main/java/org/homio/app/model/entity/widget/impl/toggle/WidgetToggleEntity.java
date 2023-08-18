package org.homio.app.model.entity.widget.impl.toggle;

import jakarta.persistence.Entity;
import org.homio.api.EntityContextWidget.ToggleType;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldLayout;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasPadding;
import org.homio.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetToggleEntity
        extends WidgetBaseEntityAndSeries<WidgetToggleEntity, WidgetToggleSeriesEntity>
        implements HasLayout, HasPadding, HasSourceServerUpdates, HasName {

    @UIField(order = 1)
    @UIFieldGroup(value = "NAME", order = 3)
    @UIFieldOptionFontSize
    public String getName() {
        return super.getName();
    }

    @Override
    @UIField(order = 50, isRevert = true)
    @UIFieldLayout(options = {"name", "value", "icon", "button"})
    @UIFieldReadDefaultValue
    public String getLayout() {
        return getJsonData("layout", getDefaultLayout());
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "HEADER", order = 5)
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
