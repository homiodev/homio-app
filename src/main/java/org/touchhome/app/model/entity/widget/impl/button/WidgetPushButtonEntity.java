package org.touchhome.app.model.entity.widget.impl.button;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIFieldLayout;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.impl.HasLayout;
import org.touchhome.app.model.entity.widget.impl.HasSourceServerUpdates;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

@Entity
public class WidgetPushButtonEntity
    extends WidgetBaseEntityAndSeries<WidgetPushButtonEntity, WidgetPushButtonSeriesEntity>
    implements HasLayout, HasSourceServerUpdates {

    public static final String PREFIX = "wgtbn_";

    @UIField(order = 31, showInContextMenu = true, icon = "fas fa-grip-vertical")
    public Boolean isVertical() {
        return getJsonData("vertical", Boolean.FALSE);
    }

    public WidgetPushButtonEntity setVertical(Boolean value) {
        setJsonData("vertical", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fa fa-stop-circle";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getBackground() {
        throw new ProhibitedExecution();
    }

    @Override
    @UIField(order = 50)
    @UIFieldLayout(options = {"name", "value", "icon"})
    public String getLayout() {
        return getJsonData("layout");
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setLayout(UIFieldLayout.LayoutBuilder
            .builder(30, 70)
            .addRow(rb ->
                rb.addCol("icon", UIFieldLayout.HorizontalAlign.center)
                  .addCol("name", UIFieldLayout.HorizontalAlign.center)).build());
    }
}
