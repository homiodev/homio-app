package org.touchhome.app.model.entity.widget.impl.slider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.HasWidgetDataSource;
import org.touchhome.bundle.api.entity.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.entity.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldNumber;
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
public class WidgetSliderSeriesEntity extends BaseEntity<WidgetSliderSeriesEntity> implements Comparable<WidgetSliderSeriesEntity>, HasWidgetDataSource {

    @UIField(order = 30)
    @UIFieldNumber(min = 0)
    private Integer min = 0;

    @UIField(order = 31)
    @UIFieldNumber(min = 0)
    private Integer max = 255;

    @UIField(order = 32)
    @UIFieldNumber(min = 1)
    private Integer step = 1;

    @UIField(order = 14, required = true)
    @UIFieldSelection(SliderSeriesDataSourceDynamicOptionLoader.class)
    private String dataSource;

    @UIField(order = 15, type = UIFieldType.Color)
    private String color = "#FFFFFF";

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private WidgetSliderEntity widgetSliderEntity;

    private int priority;

    @Override
    public int compareTo(WidgetSliderSeriesEntity widgetSliderSeriesEntity) {
        return Integer.compare(this.priority, widgetSliderSeriesEntity.priority);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(widgetSliderEntity);
    }

    @Override
    @UIField(order = 3, transparent = true)
    public String getDescription() {
        return super.getDescription();
    }

    public static class SliderSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader<Void> {

        @Override
        public List<OptionModel> loadOptions(Void parameter, BaseEntity baseEntity, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceStandaloneVariableEntity.class)
                    .add(WorkspaceVariableEntity.class)
                    .build(entityContext);
        }
    }
}
