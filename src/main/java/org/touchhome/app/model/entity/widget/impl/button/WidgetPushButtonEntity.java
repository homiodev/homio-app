package org.touchhome.app.model.entity.widget.impl.button;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.UIFieldLayout;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

import javax.persistence.Entity;

@Entity
public class WidgetPushButtonEntity extends WidgetBaseEntityAndSeries<WidgetPushButtonEntity, WidgetPushButtonSeriesEntity> {

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
    protected String getDefaultLayout() {
        return UIFieldLayout.LayoutBuilder.builder().addRow(rb -> rb
                        .addCol("icon", UIFieldLayout.HorizontalAlign.center)
                        .addCol("name", UIFieldLayout.HorizontalAlign.center))
                .build();
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
        throw new IllegalStateException("MNC");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public Boolean getShowTimeButtons() {
        throw new IllegalStateException("MNC");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public TimePeriod getTimePeriod() {
        throw new IllegalStateException("MNC");
    }
}
