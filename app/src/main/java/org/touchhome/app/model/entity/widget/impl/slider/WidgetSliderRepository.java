package org.touchhome.app.model.entity.widget.impl.slider;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetSliderRepository extends AbstractRepository<WidgetSliderEntity> {

    public WidgetSliderRepository() {
        super(WidgetSliderEntity.class, "slw_");
    }
}
