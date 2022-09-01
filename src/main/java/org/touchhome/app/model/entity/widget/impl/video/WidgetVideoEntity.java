package org.touchhome.app.model.entity.widget.impl.video;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.WidgetGroup;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.validation.MaxItems;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

import javax.persistence.Entity;
import java.util.Set;

@Entity
public class WidgetVideoEntity extends WidgetBaseEntityAndSeries<WidgetVideoEntity, WidgetVideoSeriesEntity> {

    public static final String PREFIX = "wgtvid_";

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Media;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getLayout() {
        throw new IllegalStateException("MNC");
    }

    @MaxItems(4) // allow max 4 cameras
    public Set<WidgetVideoSeriesEntity> getSeries() {
        return super.getSeries();
    }

    @Override
    public String getImage() {
        return "fas fa-video";
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setBh(3);
        setBw(3);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    // hard to validate video series
    protected boolean validateSeries(Set<WidgetVideoSeriesEntity> series, EntityContext entityContext) {
        return false;
    }
}
