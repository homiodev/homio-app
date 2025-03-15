package org.homio.app.model.entity.widget.impl.video;

import jakarta.persistence.Entity;
import org.homio.api.entity.validation.MaxItems;
import org.homio.api.entity.video.BaseStreamEntity;
import org.homio.api.model.OptionModel;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
public class WidgetVideoEntity
  extends WidgetEntityAndSeries<WidgetVideoEntity, WidgetVideoSeriesEntity> {

  public WidgetVideoEntity() {
    setBw(3);
    setBh(3);
  }

  @Override
  public WidgetGroup getGroup() {
    return WidgetGroup.Media;
  }

  @MaxItems(4) // allow max 4 cameras
  public Set<WidgetVideoSeriesEntity> getSeries() {
    return super.getSeries();
  }

  @Override
  public @NotNull String getImage() {
    return "fas fa-video";
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "video";
  }

  public List<OptionModel> getCast() {
    List<OptionModel> models = new ArrayList<>();
    List<BaseStreamEntity> services = ((ContextImpl) context()).getEntityServices(BaseStreamEntity.class);

    for (BaseStreamEntity streamService : services) {
      OptionModel model = OptionModel.entity(streamService.getStreamEntity(), null, context());
      if (!Objects.toString(model.getTitle(), "").toLowerCase().startsWith("chromecast")) {
        model.setTitle("Chromecast: " + model.getTitle());
      }
      if (streamService.getStreamPlayer().isPlaying()) {
        model.setTitle(model.getTitle() + " (Playing)");
        model.json(jsonNodes -> jsonNodes.put("playing", true));
      }
      models.add(model);
    }
    return models;
  }
}
