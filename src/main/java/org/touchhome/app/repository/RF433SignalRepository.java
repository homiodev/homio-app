package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.rf433.model.RF433SignalEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Repository
public class RF433SignalRepository extends AbstractRepository {

    public RF433SignalRepository() {
        super(RF433SignalEntity.class, "rf433_");
    }

    @Override
    @Transactional
    public BaseEntity deleteByEntityID(String entityID) {
        BaseEntity baseEntity = getByEntityID(entityID);
        RF433SignalEntity rf433SignalEntity = (RF433SignalEntity) baseEntity;
        try {
            Files.delete(Paths.get(rf433SignalEntity.getPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return super.deleteByEntityID(entityID);
    }
}
