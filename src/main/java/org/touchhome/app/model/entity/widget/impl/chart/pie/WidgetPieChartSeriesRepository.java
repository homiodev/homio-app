package org.touchhome.app.model.entity.widget.impl.chart.pie;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.List;

@Repository
public class WidgetPieChartSeriesRepository extends AbstractRepository<WidgetPieChartSeriesEntity> {

    public WidgetPieChartSeriesRepository() {
        super(WidgetPieChartSeriesEntity.class, "piesw_");
    }

    @Transactional(readOnly = true)
    public List<WidgetPieChartSeriesEntity> getByOwner(BaseEntity listOwner) {
        return findByField("widgetPieChartEntity", listOwner);
    }
}



