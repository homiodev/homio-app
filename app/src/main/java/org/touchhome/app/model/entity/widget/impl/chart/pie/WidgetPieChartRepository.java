package org.touchhome.app.model.entity.widget.impl.chart.pie;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetPieChartRepository extends AbstractRepository<WidgetPieChartEntity> {

    public WidgetPieChartRepository() {
        super(WidgetPieChartEntity.class, "piew_");
    }
}



