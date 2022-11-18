package org.touchhome.app.repository.device;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.entity.PinBaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class PinRepository extends AbstractRepository<PinBaseEntity> {

  public PinRepository() {
    super(PinBaseEntity.class);
  }
}
