package org.homio.app.model.entity.widget.impl.video;

import jakarta.persistence.Entity;
import org.homio.api.entity.validation.MaxItems;
import org.homio.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Entity
public class WidgetVideoEntity
        extends WidgetBaseEntityAndSeries<WidgetVideoEntity, WidgetVideoSeriesEntity> {

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Media;
    }

    @MaxItems(4) // allow max 4 cameras
    public Set<WidgetVideoSeriesEntity> getSeries() {
        return super.getSeries();
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-video";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "video";
    }

    @Override
    protected void beforePersist() {
        setBh(3);
        setBw(3);
        super.beforePersist();
    }
}
