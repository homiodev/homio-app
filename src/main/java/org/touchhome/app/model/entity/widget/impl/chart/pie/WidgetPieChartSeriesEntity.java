package org.touchhome.app.model.entity.widget.impl.chart.pie;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.model.entity.widget.HasDataSource;
import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.bundle.api.DynamicOptionLoader;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.model.workspace.backup.WorkspaceBackupEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldSelection;
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
public class WidgetPieChartSeriesEntity extends BaseEntity<WidgetPieChartSeriesEntity>
        implements Comparable<WidgetPieChartSeriesEntity>, HasDataSource {

    @UIField(order = 14,
            required = true,
            label = "widget.pie_dataSource")
    @UIFieldSelection(optionLoader = PieSeriesDataSourceDynamicOptionLoader.class)
    private String dataSource;

    @UIField(order = 15, type = UIFieldType.Color)
    private String color = "#FFFFFF";

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private WidgetPieChartEntity widgetPieChartEntity;

    private int priority;

    @Override
    public int compareTo(WidgetPieChartSeriesEntity entity) {
        return Integer.compare(this.priority, entity.priority);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(widgetPieChartEntity);
    }

    @Override
    @UIField(order = 3, transparent = true)
    public String getDescription() {
        return super.getDescription();
    }

    public static class PieSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<Option> loadOptions(Object parameter, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceBackupEntity.class)
                    .add(WorkspaceStandaloneVariableEntity.class)
                    .add(WorkspaceBroadcastEntity.class)
                    .build(entityContext);
        }
    }
}
