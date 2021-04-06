package org.touchhome.app.model.entity.widget.impl.display;

import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.entity.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.entity.workspace.backup.WorkspaceBackupEntity;
import org.touchhome.bundle.api.entity.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.entity.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;

import javax.persistence.Entity;
import java.util.List;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity> {

    public static final String PREFIX = "wtdps_";

    @UIField(order = 14, required = true)
    @UIFieldSelection(DisplayDataSourceDynamicOptionLoader.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 20, type = UIFieldType.ColorPicker)
    public String getForeground() {
        return getJsonData("fg", "#009688");
    }

    public WidgetDisplaySeriesEntity setForeground(String value) {
        setJsonData("fg", value);
        return this;
    }

    @UIField(order = 21, type = UIFieldType.ColorPicker)
    public String getBackground() {
        return getJsonData("bg", "rgba(0, 0, 0, 0.1)");
    }

    public WidgetDisplaySeriesEntity setBackground(String value) {
        setJsonData("bg", value);
        return this;
    }

    @UIField(order = 22)
    public String getPrepend() {
        return getJsonData("prepend");
    }

    public WidgetDisplaySeriesEntity setPrepend(String value) {
        setJsonData("prepend", value);
        return this;
    }


    @UIField(order = 23)
    public String getAppend() {
        return getJsonData("append");
    }

    public WidgetDisplaySeriesEntity setAppend(String value) {
        setJsonData("append", value);
        return this;
    }

    @UIField(order = 24)
    public boolean getShowLastUpdateDate() {
        return getJsonData("showLastUpdateDate", Boolean.FALSE);
    }

    public WidgetDisplaySeriesEntity setShowLastUpdateDate(boolean value) {
        setJsonData("showLastUpdateDate", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public static class DisplayDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(BaseEntity baseEntity, EntityContext entityContext, String[] staticParameters) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceStandaloneVariableEntity.class)
                    .add(WorkspaceVariableEntity.class)
                    .add(WorkspaceBackupEntity.class)
                    .add(WorkspaceBooleanEntity.class)
                    .build(entityContext);
        }
    }
}
