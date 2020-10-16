package org.touchhome.app.model.entity.widget.impl.toggle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.model.entity.widget.HasDataSource;
import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.bundle.api.DynamicOptionLoader;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import java.util.List;
import java.util.Set;

@Setter
@Getter
@Accessors(chain = true)
@Entity
public class WidgetToggleSeriesEntity extends BaseEntity<WidgetToggleSeriesEntity> implements Comparable<WidgetToggleSeriesEntity>, HasDataSource {

    @UIField(order = 14,
            required = true,
            label = "widget.toggle_dataSource")
    @UIFieldSelection(ToggleSeriesDataSourceDynamicOptionLoader.class)
    private String dataSource;

    @UIField(order = 15)
    private String onName = "On";

    @UIField(order = 16)
    private String offName = "Off";

    @UIField(order = 17, type = UIFieldType.Color)
    private String color = "#FFFFFF";

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private WidgetToggleEntity widgetToggleEntity;

    private int priority;

    @Override
    public int compareTo(WidgetToggleSeriesEntity lineChartSeriesEntity) {
        return Integer.compare(this.priority, lineChartSeriesEntity.priority);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(widgetToggleEntity);
    }

    @Override
    @UIField(order = 3, transparent = true)
    public String getDescription() {
        return super.getDescription();
    }

    public static class ToggleSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader<Void> {

        @Override
        public List<Option> loadOptions(Void parameter, BaseEntity baseEntity, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceBooleanEntity.class)
                    .build(entityContext);
        }
    }
}
