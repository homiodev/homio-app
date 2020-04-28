package org.touchhome.app.model.entity.widget.impl.slider;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetSliderSeriesRepository extends AbstractRepository<WidgetSliderSeriesEntity> {

    public WidgetSliderSeriesRepository() {
        super(WidgetSliderSeriesEntity.class, "ssw_");
    }
}
