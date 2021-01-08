package org.touchhome.app.repository.widget.impl.chart;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetLineChartRepository extends AbstractRepository<WidgetLineChartEntity> {

    public WidgetLineChartRepository() {
        super(WidgetLineChartEntity.class, "lcw_");
    }
}



