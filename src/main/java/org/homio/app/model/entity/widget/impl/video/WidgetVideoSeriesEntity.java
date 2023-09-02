package org.homio.app.model.entity.widget.impl.video;

import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.api.model.OptionModel;
import org.homio.api.model.StylePosition;
import org.homio.api.ui.field.UIField;
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

    @UIField(order = 13)
    public boolean isShowPictureInPicture() {
        return getJsonData("pip", false);
    }

    public void setShowPictureInPicture(boolean value) {
        setJsonData("pip", value);
    }

    @UIField(order = 14)
    public boolean isShowCurrentTime() {
        return getJsonData("ct", true);
    }

    public void setShowCurrentTime(boolean value) {
        setJsonData("ct", value);
    }

    @UIField(order = 15)
    public boolean isInvertTime() {
        return getJsonData("it", false);
    }

    public void setInvertTime(boolean value) {
        setJsonData("it", value);
    }

    @UIField(order = 16)
    public boolean isShowDownloads() {
        return getJsonData("dd", false);
    }

    public void setShowDownloads(boolean value) {
        setJsonData("dd", value);
    }

    @UIField(order = 18)
    public boolean isShowFastForward() {
        return getJsonData("ff", false);
    }

    public void setShowFastForward(boolean value) {
        setJsonData("ff", value);
    }

    @UIField(order = 19)
    public boolean isShowRewind() {
        return getJsonData("rw", false);
    }

    public void setShowRewind(boolean value) {
        setJsonData("rw", value);
    }

    @Override
    @UIField(order = 14, required = true, label = "widget.video_dataSource")
    @UIFieldDynamicSelection(value = VideoSeriesDataSourceDynamicOptionLoader.class, icon = "fas fa-film", iconColor = "#FF00FF")
    @UIFieldTreeNodeSelection(pattern = ".*(\\.jpg|\\.webm|\\.ogv|\\.flv|\\.avi|\\.mpd|\\.mp4|\\.m3u8)", iconColor = "#14A669", prefix = "file://")
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    public StylePosition getActionPosition() {
        return getJsonDataEnum("actionPosition", StylePosition.TopRight);
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
            for (BaseVideoEntity<?, ?> entity : parameters.getEntityContext().findAll(BaseVideoEntity.class)) {
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
}
