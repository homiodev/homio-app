package org.touchhome.app.repository.widget.impl.chart;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.List;

@Repository
public class WidgetBarChartSeriesRepository extends AbstractRepository<WidgetBarChartSeriesEntity> {

    public WidgetBarChartSeriesRepository() {
        super(WidgetBarChartSeriesEntity.class, "barsw_");
    }

    @Transactional(readOnly = true)
    public List<WidgetBarChartSeriesEntity> getByOwner(BaseEntity listOwner) {
        return findByField("widgetBarChartEntity", listOwner);
    }
}



