package org.touchhome.app.model.entity.widget.impl.chart.line;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.List;

@Repository
public class WidgetLineChartSeriesRepository extends AbstractRepository<WidgetLineChartSeriesEntity> {

    public WidgetLineChartSeriesRepository() {
        super(WidgetLineChartSeriesEntity.class, "csw_");
    }

    @Transactional(readOnly = true)
    public List<WidgetLineChartSeriesEntity> getByOwner(BaseEntity listOwner) {
        List<WidgetLineChartSeriesEntity> list = findByField("widgetLineChartEntity", listOwner);
        return list;
    }
}



