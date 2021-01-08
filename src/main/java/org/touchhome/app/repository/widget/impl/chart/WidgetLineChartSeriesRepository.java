package org.touchhome.app.repository.widget.impl.chart;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.touchhome.bundle.api.entity.BaseEntity;
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



