package org.touchhome.app.model.entity.widget.impl.gauge;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetGaugeRepository extends AbstractRepository<WidgetGaugeEntity> {

    public WidgetGaugeRepository() {
        super(WidgetGaugeEntity.class, "ggw_");
    }
}
