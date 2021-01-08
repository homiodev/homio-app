package org.touchhome.app.model.entity.widget.impl.button;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.HasWidgetDataSource;
import org.touchhome.bundle.api.entity.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import java.util.List;
import java.util.Set;

@Setter
@Getter
@Accessors(chain = true)
@Entity
public class WidgetButtonSeriesEntity extends BaseEntity<WidgetButtonSeriesEntity> implements Comparable<WidgetButtonSeriesEntity>, HasWidgetDataSource {

    @UIField(order = 14,
            required = true,
            label = "widget.button_dataSource")
    @UIFieldSelection(ButtonsSeriesDataSourceDynamicOptionLoader.class)
    private String dataSource;

    @UIField(order = 15, type = UIFieldType.Color)
    private String color = "#FFFFFF";

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private WidgetButtonsEntity widgetButtonsEntity;

    private int priority;

    @Override
    public int compareTo(WidgetButtonSeriesEntity entity) {
        return Integer.compare(this.priority, entity.priority);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(widgetButtonsEntity);
    }

    @Override
    @UIField(order = 3, transparent = true)
    public String getDescription() {
        return super.getDescription();
    }

    public static class ButtonsSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader<Void> {

        @Override
        public List<OptionModel> loadOptions(Void parameter, BaseEntity baseEntity, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceBooleanEntity.class)
                    .add(WorkspaceBroadcastEntity.class)
                    .build(entityContext);
        }
    }
}
