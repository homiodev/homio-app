package org.touchhome.app.model.entity.widget.impl.toggle;

import org.touchhome.app.model.entity.widget.SeriesBuilder;
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
public class WidgetToggleSeriesEntity extends WidgetSeriesEntity<WidgetToggleEntity> {

    public static final String PREFIX = "wttgs_";

    @UIField(order = 14, required = true)
    @UIFieldSelection(ToggleSeriesDataSourceDynamicOptionLoader.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15)
    public String getOnName() {
        return getJsonData("onName", "On");
    }

    public WidgetToggleSeriesEntity setOnName(String value) {
        setJsonData("onName", value);
        return this;
    }

    @UIField(order = 16)
    public String getOffName() {
        return getJsonData("offName", "Off");
    }

    public WidgetToggleSeriesEntity setOffName(String value) {
        setJsonData("offName", value);
        return this;
    }

    @UIField(order = 17, type = UIFieldType.Color)
    public String getColor() {
        return getJsonData("color", "#FFFFFF");
    }

    public WidgetToggleSeriesEntity setColor(String value) {
        setJsonData("color", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public static class ToggleSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(BaseEntity baseEntity, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceBooleanEntity.class)
                    .build(entityContext);
        }
    }
}
