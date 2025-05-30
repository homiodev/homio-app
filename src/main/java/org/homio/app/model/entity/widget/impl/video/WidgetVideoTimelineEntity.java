package org.homio.app.model.entity.widget.impl.video;

import jakarta.persistence.Entity;
import org.homio.addon.camera.entity.CameraPlaybackStorage;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldNumber;
import org.homio.api.ui.field.selection.dynamic.DynamicOptionLoader;
import org.homio.api.ui.field.selection.dynamic.UIFieldDynamicSelection;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
public class WidgetVideoTimelineEntity extends WidgetEntity<WidgetVideoTimelineEntity> {

  public WidgetVideoTimelineEntity() {
    setBh(4);
    setBw(3);
  }

  @Override
  public WidgetGroup getGroup() {
    return WidgetGroup.Media;
  }

  @UIField(order = 33, showInContextMenu = true)
  public Boolean getShowButtons() {
    return getJsonData("showButtons", Boolean.TRUE);
  }

  public WidgetVideoTimelineEntity setShowButtons(Boolean value) {
    setJsonData("showButtons", value);
    return this;
  }

  @UIFieldNumber(min = 2, max = 365)
  public int getTimePeriodDays() {
    return getJsonData("period", 30);
  }

  public WidgetVideoTimelineEntity setTimePeriodDays(int value) {
    setJsonData("period", value);
    return this;
  }

  @UIField(order = 14, required = true, label = "widget.video_dataSource")
  @UIFieldDynamicSelection(WidgetVideoTimelineEntity.VideoTimelineDataSourceDynamicOptionLoader.class)
  public String getDataSource() {
    return getJsonData("ds");
  }

  public WidgetVideoTimelineEntity setDataSource(String value) {
    setJsonData("ds", value);
    return this;
  }

  @Override
  public @NotNull String getImage() {
    return "fas fa-boxes";
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
    if (getDataSource().isEmpty()) {
      fields.add("dataSource");
    }
  }

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "video-time";
  }

  public static class VideoTimelineDataSourceDynamicOptionLoader implements DynamicOptionLoader {

    @Override
    public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
      List<OptionModel> list = new ArrayList<>();
      for (IpCameraEntity entity : parameters.context().db().findAll(IpCameraEntity.class)) {
        if (entity.getService().getBrandHandler() instanceof CameraPlaybackStorage) {
          list.add(OptionModel.of(entity.getEntityID(), entity.getTitle()));
        }
      }
      return list;
    }
  }
}
