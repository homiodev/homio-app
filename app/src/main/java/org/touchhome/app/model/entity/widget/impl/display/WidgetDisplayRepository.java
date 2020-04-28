package org.touchhome.app.model.entity.widget.impl.display;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetDisplayRepository extends AbstractRepository<WidgetDisplayEntity> {

    public WidgetDisplayRepository() {
        super(WidgetDisplayEntity.class, "dw_");
    }
}
