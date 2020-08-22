package org.touchhome.app.repository.widget;

import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.Date;
import java.util.List;

public interface HasFetchChartSeries {

    List<Object[]> getLineChartSeries(BaseEntity source, Date from, Date to);

    Object getPieChartSeries(BaseEntity source, Date from, Date to, WidgetPieChartEntity.PieChartValueType pieChartValueType);

    Date getMinDate(BaseEntity source);

    default Query buildValuesQuery(EntityManager em, String queryName, BaseEntity baseEntity, Date from, Date to) {
        return em.createNamedQuery(queryName)
                .setParameter("source", baseEntity)
                .setParameter("from", from)
                .setParameter("to", to);
    }
}
