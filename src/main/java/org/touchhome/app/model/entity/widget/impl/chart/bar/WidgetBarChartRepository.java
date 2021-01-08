package org.touchhome.app.model.entity.widget.impl.chart.bar;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetBarChartRepository extends AbstractRepository<WidgetBarChartEntity> {

    public WidgetBarChartRepository() {
        super(WidgetBarChartEntity.class, "barw_");
    }
}
