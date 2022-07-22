package org.touchhome.app.model.entity.widget.impl.display;

import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;

import javax.persistence.Entity;

@Entity
public class WidgetDisplayEntity extends WidgetBaseEntityAndSeries<WidgetDisplayEntity, WidgetDisplaySeriesEntity> {

    public static final String PREFIX = "wgtdp_";

    @Override
    public String getImage() {
        return "fas fa-tv";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
