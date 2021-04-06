package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.entity.MessengerEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository("messengerRepository")
public class MessengerRepository extends AbstractRepository<MessengerEntity> {

    public MessengerRepository() {
        super(MessengerEntity.class);
    }
}
