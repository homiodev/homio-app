package org.touchhome.app.model.entity.widget.impl.chart.line;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetLineChartRepository extends AbstractRepository<WidgetLineChartEntity> {

    public WidgetLineChartRepository() {
        super(WidgetLineChartEntity.class, "lcw_");
    }
}



