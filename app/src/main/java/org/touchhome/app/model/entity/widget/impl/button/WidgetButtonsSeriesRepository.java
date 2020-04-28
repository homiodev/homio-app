package org.touchhome.app.model.entity.widget.impl.button;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetButtonsSeriesRepository extends AbstractRepository<WidgetButtonSeriesEntity> {

    public WidgetButtonsSeriesRepository() {
        super(WidgetButtonSeriesEntity.class, "bws_");
    }
}



