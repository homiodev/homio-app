package org.touchhome.app.model.entity.widget.impl.display;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;

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

    @Override
    @JsonIgnore
    public String getLayout() {
        throw new IllegalStateException("MNC");
    }
}
