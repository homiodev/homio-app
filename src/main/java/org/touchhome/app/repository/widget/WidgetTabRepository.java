package org.touchhome.app.repository.widget;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.WidgetTabEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Log4j2
@Repository
public class WidgetTabRepository extends AbstractRepository<WidgetTabEntity> {

  public WidgetTabRepository() {
    super(WidgetTabEntity.class);
  }
}
