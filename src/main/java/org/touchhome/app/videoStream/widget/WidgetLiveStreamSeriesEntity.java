package org.touchhome.app.videoStream.widget;

import org.touchhome.app.videoStream.entity.BaseVideoStreamEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.model.StylePosition;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;

import javax.persistence.Entity;
import java.util.List;

@Entity
public class WidgetLiveStreamSeriesEntity extends WidgetSeriesEntity<WidgetLiveStreamEntity> {

    public static final String PREFIX = "wtvds_";

    @UIField(order = 14, required = true, label = "widget.video_dataSource")
    @UIFieldSelection(VideoSeriesDataSourceDynamicOptionLoader.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    public StylePosition getActionPosition() {
        return getJsonDataEnum("actionPosition", StylePosition.TopRight);
    }

    public WidgetLiveStreamSeriesEntity setActionPosition(StylePosition value) {
        setJsonData("actionPosition", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public static class VideoSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(BaseEntity baseEntity, EntityContext entityContext) {
            return OptionModel.list(entityContext.findAll(BaseVideoStreamEntity.class));
        }
    }
}
