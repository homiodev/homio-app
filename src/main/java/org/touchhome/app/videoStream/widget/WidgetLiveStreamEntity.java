package org.touchhome.app.videoStream.widget;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;

import javax.persistence.Entity;
import javax.validation.constraints.Max;
import java.util.Set;

@Entity
public class WidgetLiveStreamEntity extends WidgetBaseEntityAndSeries<WidgetLiveStreamEntity, WidgetLiveStreamSeriesEntity> {

    public static final String PREFIX = "wtlstr_";

    @Max(4) // allow max 4 cameras
    public Set<WidgetLiveStreamSeriesEntity> getSeries() {
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
    public boolean updateRelations(EntityContext entityContext) {
        return false;
    }
}
