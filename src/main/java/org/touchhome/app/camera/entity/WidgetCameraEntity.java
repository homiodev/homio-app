package org.touchhome.app.camera.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.*;
import javax.validation.constraints.Max;
import java.util.Set;

@Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetCameraEntity extends WidgetBaseEntity<WidgetCameraEntity> {

    @OrderBy("priority asc")
    @UIField(order = 30, onlyEdit = true)
    @Max(4) // allow max 4 cameras
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetCameraEntity")
    private Set<WidgetCameraSeriesEntity> series;

    @UIField(order = 32)
    private boolean showPlayPause = true;

    @UIField(order = 33)
    private boolean showVolume = true;

    @UIField(order = 34)
    private boolean showCurrentTime = true;

    @UIField(order = 35)
    private boolean showTotalTime = false;

    @UIField(order = 36)
    private boolean showFullScreen = false;

    @UIField(order = 37)
    private boolean showSpeed = false;

    @UIField(order = 38)
    private boolean showMute = false;

    @UIField(order = 39)
    private boolean showLeftTime = false;

    @Override
    public String getImage() {
        return "fas fa-video";
    }

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        return false;
    }
}
