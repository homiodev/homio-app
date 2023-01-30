package org.touchhome.app.model.entity.widget.impl.video;

import java.util.Set;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.WidgetGroup;
import org.touchhome.bundle.api.entity.validation.MaxItems;

@Entity
public class WidgetVideoEntity
        extends WidgetBaseEntityAndSeries<WidgetVideoEntity, WidgetVideoSeriesEntity> {

    public static final String PREFIX = "wgtvid_";

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Media;
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
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        setBh(3);
        setBw(3);
        super.beforePersist();
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
