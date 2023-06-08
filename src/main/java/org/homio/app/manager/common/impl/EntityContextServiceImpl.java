package org.homio.app.manager.common.impl;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.mqtt.entity.MQTTBaseEntity;
import org.homio.api.EntityContextService;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.app.manager.common.EntityContextImpl;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class EntityContextServiceImpl implements EntityContextService {

    public static final Map<String, Class<? extends HasEntityIdentifier>> entitySelectMap = new HashMap<>();

    static {
        entitySelectMap.put(EntityContextService.MQTT_SERVICE, MQTTBaseEntity.class);
    }

    @Getter
    private final EntityContextImpl entityContext;

    public EntityContextServiceImpl(EntityContextImpl entityContext) {
        this.entityContext = entityContext;
    }

    @Override
    public void registerEntityTypeForSelection(@NotNull Class<? extends HasEntityIdentifier> entityClass, @NotNull String type) {
        if (entitySelectMap.containsKey(type)) {
            throw new IllegalArgumentException("Entity type: '" + type + "' already registered");
        }
        entitySelectMap.put(type, entityClass);
    }
}
