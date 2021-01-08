package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetSliderSeriesRepository extends AbstractRepository<WidgetSliderSeriesEntity> {

    public WidgetSliderSeriesRepository() {
        super(WidgetSliderSeriesEntity.class, "wtsls_");
    }
}
