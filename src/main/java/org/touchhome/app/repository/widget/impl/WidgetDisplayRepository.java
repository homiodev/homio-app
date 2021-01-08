package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetDisplayRepository extends AbstractRepository<WidgetDisplayEntity> {

    public WidgetDisplayRepository() {
        super(WidgetDisplayEntity.class, "wtdp_");
    }
}
