package org.touchhome.app.model.entity.widget.impl.button;

import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.entity.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;

import javax.persistence.Entity;
import java.util.List;

@Entity
public class WidgetButtonSeriesEntity extends WidgetSeriesEntity<WidgetButtonEntity> {

    public static final String PREFIX = "wtbs_";

    @UIField(order = 14, required = true)
    @UIFieldSelection(ButtonsSeriesDataSourceDynamicOptionLoader.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 17, type = UIFieldType.Color)
    public String getColor() {
        return getJsonData("color", "#FFFFFF");
    }

    public WidgetButtonSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public static class ButtonsSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(BaseEntity baseEntity, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceBooleanEntity.class)
                    .add(WorkspaceBroadcastEntity.class)
                    .build(entityContext);
        }
    }
}
