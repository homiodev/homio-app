package org.homio.app.model.entity.widget.impl.video;

import jakarta.persistence.Entity;
import java.util.Set;
import org.homio.api.entity.validation.MaxItems;
import org.homio.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetGroup;

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
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        setBh(3);
        setBw(3);
        super.beforePersist();
    }
}
