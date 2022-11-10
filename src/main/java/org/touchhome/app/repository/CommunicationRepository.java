package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.entity.types.CommunicationEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository("communicationRepository")
public class CommunicationRepository extends AbstractRepository<CommunicationEntity> {

  public CommunicationRepository() {
    super(CommunicationEntity.class);
  }
}
