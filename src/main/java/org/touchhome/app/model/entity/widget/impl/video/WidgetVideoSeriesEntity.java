package org.touchhome.app.model.entity.widget.impl.video;

import org.springframework.data.util.Pair;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.model.StylePosition;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.selection.UIFieldFileSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;
import org.touchhome.bundle.api.video.BaseVideoStreamEntity;

import javax.persistence.Entity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
public class WidgetVideoSeriesEntity extends WidgetSeriesEntity<WidgetVideoEntity> {

    public static final String PREFIX = "wtvidser_";

    @UIField(order = 11)
    public boolean isAutoPlay() {
        return getJsonData("ap", false);
    }

    public void setAutoPlay(boolean value) {
        setJsonData("ap", value);
    }

    @UIField(order = 12)
    public boolean isShowRestartButton() {
        return getJsonData("rb", false);
    }

    public void setShowRestartButton(boolean value) {
        setJsonData("rb", value);
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

    @UIField(order = 17)
    public boolean isShowFullScreen() {
        return getJsonData("fs", true);
    }

    public void setShowFullScreen(boolean value) {
        setJsonData("fs", value);
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

    @UIField(order = 14, required = true, label = "widget.video_dataSource")
    @UIFieldSelection(value = VideoSeriesDataSourceDynamicOptionLoader.class, allowInputRawText = true)
    @UIFieldFileSelection(extensions = {".mp4", ".m3u8"}, iconColor = "#14A669")
    public String getDataSource() {
        return getJsonData("ds");
    }

    public StylePosition getActionPosition() {
        return getJsonDataEnum("actionPosition", StylePosition.TopRight);
    }

    public WidgetVideoSeriesEntity setActionPosition(StylePosition value) {
        setJsonData("actionPosition", value);
        return this;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public static class VideoSeriesDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            List<OptionModel> list = new ArrayList<>();
            for (BaseVideoStreamEntity entity : parameters.getEntityContext().findAll(BaseVideoStreamEntity.class)) {
                OptionModel model = OptionModel.of(entity.getEntityID(), entity.getTitle());

                Collection<Pair<String, String>> sources = entity.getVideoSources();
                if (!sources.isEmpty()) {
                    for (Pair<String, String> source : sources) {
                        model.addChild(OptionModel.of(source.getFirst(), source.getSecond()));
                    }
                    list.add(model);
                }
            }
            return list;
        }
    }
}