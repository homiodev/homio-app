package org.homio.app.model.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.app.model.entity.widget.WidgetEntity;

@Getter
@Setter
public class WidgetDataRequest {

  @NotNull
  private String entityID;
  private String liveEntity;

  @SneakyThrows
  public <T extends BaseEntity> T getEntity(
    Context context, ObjectMapper objectMapper, Class<T> tClass) {
    if (liveEntity != null) {
      return objectMapper.readValue(liveEntity, tClass);
    }
    return context.db().get(entityID);
  }

  @SneakyThrows
  public WidgetEntity getEntity(Context context, ObjectMapper objectMapper) {
    WidgetEntity entity = context.db().get(entityID);
    if (liveEntity != null) {
      return objectMapper.readValue(liveEntity, entity.getClass());
    }
    return context.db().get(entityID);
  }
}
