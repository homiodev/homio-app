package org.touchhome.bundle.arduino.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.repository.AbstractDeviceRepository;
import org.touchhome.bundle.arduino.model.ArduinoDeviceEntity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class ArduinoDeviceRepository extends AbstractDeviceRepository<ArduinoDeviceEntity> {

    public static final String PREFIX = "ad_";
    private EntityContext entityContext;

    public ArduinoDeviceRepository(EntityContext entityContext) {
        super(ArduinoDeviceEntity.class, PREFIX);
        this.entityContext = entityContext;
    }

    public long getPipeIndex(long startFrom) {
        List<ArduinoDeviceEntity> entities = entityContext.findAll(ArduinoDeviceEntity.class);
        Set<Long> items = entities.stream().map(ArduinoDeviceEntity::getPipe).collect(Collectors.toSet());
        while (items.contains(startFrom)) {
            startFrom++;
        }
        return startFrom;
    }
}
