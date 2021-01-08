package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.gauge.WidgetGaugeEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetGaugeRepository extends AbstractRepository<WidgetGaugeEntity> {

    public WidgetGaugeRepository() {
        super(WidgetGaugeEntity.class, "wtgg_");
    }
}
