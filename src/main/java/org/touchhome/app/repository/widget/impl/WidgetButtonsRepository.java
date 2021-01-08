package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.button.WidgetButtonsEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetButtonsRepository extends AbstractRepository<WidgetButtonsEntity> {

    public WidgetButtonsRepository() {
        super(WidgetButtonsEntity.class, "wtbn_");
    }
}



