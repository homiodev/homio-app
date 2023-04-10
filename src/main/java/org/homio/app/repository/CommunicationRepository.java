package org.homio.app.repository;

import org.homio.bundle.api.entity.types.CommunicationEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository("communicationRepository")
public class CommunicationRepository extends AbstractRepository<CommunicationEntity> {

    public CommunicationRepository() {
        super(CommunicationEntity.class);
    }
}