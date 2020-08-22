package org.touchhome.app.model.entity.widget.impl.toggle;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetToggleRepository extends AbstractRepository<WidgetToggleEntity> {

    public WidgetToggleRepository() {
        super(WidgetToggleEntity.class, "tw_");
    }
}
