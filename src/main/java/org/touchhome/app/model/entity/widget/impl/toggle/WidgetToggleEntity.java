package org.touchhome.app.model.entity.widget.impl.toggle;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIFieldLayout;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.impl.HasLayout;
import org.touchhome.app.model.entity.widget.impl.HasSourceServerUpdates;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

@Entity
public class WidgetToggleEntity
    extends WidgetBaseEntityAndSeries<WidgetToggleEntity, WidgetToggleSeriesEntity>
    implements HasLayout, HasSourceServerUpdates {

    public static final String PREFIX = "wgttg_";

    @Override
    @UIField(order = 50)
    @UIFieldLayout(options = {"name", "value", "icon", "button"})
    public String getLayout() {
        return getJsonData("layout");
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Header")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Header", order = 1)
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
    public String getImage() {
        return "fas fa-toggle-on";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setLayout(UIFieldLayout.LayoutBuilder
            .builder(10, 60, 30)
            .addRow(rb ->
                rb.addCol("icon", UIFieldLayout.HorizontalAlign.right)
                  .addCol("name", UIFieldLayout.HorizontalAlign.left)
                  .addCol("button", UIFieldLayout.HorizontalAlign.center))
            .build());
    }

    enum ToggleType {
        Regular,
        Slide
    }
}
