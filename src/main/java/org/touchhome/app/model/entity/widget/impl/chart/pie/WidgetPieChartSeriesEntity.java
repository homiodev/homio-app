package org.touchhome.app.model.entity.widget.impl.chart.pie;

import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.entity.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.entity.workspace.backup.WorkspaceBackupEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;

import javax.persistence.Entity;
import java.util.List;

@Entity
public class WidgetPieChartSeriesEntity extends WidgetSeriesEntity<WidgetPieChartEntity> {

    public static final String PREFIX = "piesw_";

    @UIField(order = 14, required = true)
    @UIFieldSelection(PieSeriesDataSourceDynamicOptionLoader.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15, type = UIFieldType.ColorPicker)
    public String getColor() {
        return getJsonData("color", "#FFFFFF");
    }

    public WidgetPieChartSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public static class PieSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(BaseEntity baseEntity, EntityContext entityContext, String[] staticParameters) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceBackupEntity.class)
                    .add(WorkspaceStandaloneVariableEntity.class)
                    .add(WorkspaceBroadcastEntity.class)
                    .build(entityContext);
        }
    }
}
