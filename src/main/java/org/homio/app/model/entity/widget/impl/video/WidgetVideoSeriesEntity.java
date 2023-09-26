package org.homio.app.model.entity.widget.impl.video;

import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.api.model.OptionModel;
import org.homio.api.model.StylePosition;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnoreParent;
import org.homio.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;

@SuppressWarnings("unused")
@Entity
public class WidgetVideoSeriesEntity extends WidgetSeriesEntity<WidgetVideoEntity>
        implements HasSingleValueDataSource {

    @UIField(order = 11)
    public boolean isAutoPlay() {
        return getJsonData("ap", false);
    }

    public void setAutoPlay(boolean value) {
        setJsonData("ap", value);
    }

    @Override
    @UIField(order = 14, required = true, label = "widget.video_dataSource")
    @UIFieldDynamicSelection(value = VideoSeriesDataSourceDynamicOptionLoader.class, icon = "fas fa-film", iconColor = "#7899D0")
    @UIFieldTreeNodeSelection(pattern = ".*(\\.png|\\.jpg|\\.webm|\\.ogv|\\.flv|\\.avi|\\.mpd|\\.mp4|\\.m3u8)", iconColor = "#14A669")
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    public StylePosition getActionPosition() {
        return getJsonDataEnum("actionPosition", StylePosition.TopRight);
    }

    @UIField(order = 40)
    @UIFieldGroup("VALUE")
    @UIFieldDynamicSelection(value = VideoPosterDataSourceDynamicOptionLoader.class, icon = "fas fa-image", iconColor = "#5E8FAD")
    @UIFieldTreeNodeSelection(pattern = ".*(\\.jpg|\\.png)", iconColor = "#14A669")
    public String getPosterDataSource() {
        return getJsonData("poster");
    }

    public void setPosterDataSource(String value) {
        setJsonData("poster", value);
    }

    public WidgetVideoSeriesEntity setActionPosition(StylePosition value) {
        setJsonData("actionPosition", value);
        return this;
    }

    @Override
    protected String getSeriesPrefix() {
        return "video";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    public static class VideoSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            List<OptionModel> list = new ArrayList<>();
            for (BaseCameraEntity<?, ?> entity : parameters.getEntityContext().findAll(BaseCameraEntity.class)) {
                List<OptionModel> sources = entity.getVideoSources();
                if (!sources.isEmpty()) {
                    OptionModel model = OptionModel
                        .of(entity.getEntityID(), entity.getTitle())
                        .setIcon(entity.getEntityIcon());
                    model.setChildren(sources);
                    list.add(model);
                }
            }
            return list;
        }
    }

    public static class VideoPosterDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            List<OptionModel> list = new ArrayList<>();
            String entityID = parameters.getBaseEntity().getEntityID();
            list.add(OptionModel.of("$DEVICE_URL/rest/media/video/%s/snapshot.jpg".formatted(entityID), "snapshot.jpg"));
            return list;
        }
    }
}
