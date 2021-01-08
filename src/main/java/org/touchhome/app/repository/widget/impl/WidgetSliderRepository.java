package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetSliderRepository extends AbstractRepository<WidgetSliderEntity> {

    public WidgetSliderRepository() {
        super(WidgetSliderEntity.class, "wtsl_");
    }
}
