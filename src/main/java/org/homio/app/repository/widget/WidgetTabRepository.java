package org.homio.app.repository.widget;

import lombok.extern.log4j.Log4j2;
import org.homio.api.repository.AbstractRepository;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class WidgetTabRepository extends AbstractRepository<WidgetTabEntity> {

    public WidgetTabRepository() {
        super(WidgetTabEntity.class);
    }
}
