package org.touchhome.app.model.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class WidgetDataRequest {
    @NotNull
    private String entityID;
    private String liveEntity;

    @SneakyThrows
    public <T extends BaseEntity> T getEntity(EntityContext entityContext, ObjectMapper objectMapper, Class<T> tClass) {
        if (liveEntity != null) {
            return objectMapper.readValue(liveEntity, tClass);
        }
        return entityContext.getEntity(entityID);
    }
}
