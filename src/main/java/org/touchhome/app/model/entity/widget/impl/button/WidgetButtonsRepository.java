package org.touchhome.app.model.entity.widget.impl.button;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetButtonsRepository extends AbstractRepository<WidgetButtonsEntity> {

    public WidgetButtonsRepository() {
        super(WidgetButtonsEntity.class, "bw_");
    }
}



