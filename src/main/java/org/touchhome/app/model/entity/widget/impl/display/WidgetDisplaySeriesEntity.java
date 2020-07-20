package org.touchhome.app.model.entity.widget.impl.display;

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
import org.touchhome.bundle.api.model.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.model.workspace.backup.WorkspaceBackupEntity;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldTargetSelection;
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
public class WidgetDisplaySeriesEntity extends BaseEntity<WidgetDisplaySeriesEntity> implements Comparable<WidgetDisplaySeriesEntity>, HasDataSource {

    @UIField(order = 14,
            required = true,
            label = "widget.display_dataSource")
    @UIFieldTargetSelection(target = DisplayDataSourceDynamicOptionLoader.class)
    private String dataSource;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private WidgetDisplayEntity widgetDisplayEntity;

    private int priority;

    @UIField(order = 20, type = UIFieldType.Color)
    private String foreground = "#009688";

    @UIField(order = 21, type = UIFieldType.Color)
    private String background = "rgba(0, 0, 0, 0.1)";

    @UIField(order = 22)
    private String prepend = "";

    @UIField(order = 23)
    private String append = "";

    @UIField(order = 24)
    private Boolean showLastUpdateDate = Boolean.TRUE;

    @Override
    public int compareTo(WidgetDisplaySeriesEntity entity) {
        return Integer.compare(this.priority, entity.priority);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(widgetDisplayEntity);
    }

    @Override
    @UIField(order = 3, transparent = true)
    public String getDescription() {
        return super.getDescription();
    }

    public static class DisplayDataSourceDynamicOptionLoader implements DynamicOptionLoader<Void> {

        @Override
        public List<Option> loadOptions(Void parameter, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceStandaloneVariableEntity.class)
                    .add(WorkspaceVariableEntity.class)
                    .add(WorkspaceBackupEntity.class)
                    .add(WorkspaceBooleanEntity.class)
                    .build(entityContext);
        }
    }
}
