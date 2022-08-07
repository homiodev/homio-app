package org.touchhome.app.model.entity.widget.impl.button;

import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;
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
    public String getImage() {
        return "fa fa-stop-circle";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    @UIFieldIgnore
    public String getBackground() {
        return super.getBackground();
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowTimeButtons() {
        return super.getShowTimeButtons();
    }

    @Override
    @UIFieldIgnore
    public TimePeriod getTimePeriod() {
        return super.getTimePeriod();
    }
}
