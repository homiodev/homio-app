package org.touchhome.app.camera.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.sarxos.webcam.Webcam;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.HasWidgetDataSource;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Setter
@Getter
@Accessors(chain = true)
@Entity
public class WidgetCameraSeriesEntity extends BaseEntity<WidgetCameraSeriesEntity> implements Comparable<WidgetCameraSeriesEntity>, HasWidgetDataSource {

    @UIField(order = 14, required = true, label = "widget.video_dataSource")
    @UIFieldSelection(VideoSeriesDataSourceDynamicOptionLoader.class)
    private String dataSource;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private WidgetCameraEntity widgetCameraEntity;

    private int priority;

    @Override
    public int compareTo(WidgetCameraSeriesEntity entity) {
        return Integer.compare(this.priority, entity.priority);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(widgetCameraEntity);
    }

    @Override
    @UIField(order = 3, transparent = true)
    public String getDescription() {
        return super.getDescription();
    }

    public static class VideoSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader<Object> {

        @Override
        public List<OptionModel> loadOptions(Object parameter, BaseEntity baseEntity, EntityContext entityContext) {
            List<OptionModel> list = new ArrayList<>();
            for (Webcam webcam : Webcam.getWebcams()) {
                list.add(OptionModel.of(webcam.getName(), webcam.getName()));
            }
            return list;
        }
    }
}
