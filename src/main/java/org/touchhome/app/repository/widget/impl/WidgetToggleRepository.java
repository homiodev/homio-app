package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetToggleRepository extends AbstractRepository<WidgetToggleEntity> {

    public WidgetToggleRepository() {
        super(WidgetToggleEntity.class, "wttg_");
    }
}
