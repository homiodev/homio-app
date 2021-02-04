package org.touchhome.app.repository.workspace;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.app.repository.widget.HasFetchChartSeries;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.exception.ServerException;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.Date;
import java.util.List;

@Repository("broadcastRepository")
public class WorkspaceBroadcastRepository extends AbstractRepository<WorkspaceBroadcastEntity> implements HasFetchChartSeries {

    public WorkspaceBroadcastRepository() {
        super(WorkspaceBroadcastEntity.class);
    }

    @Override
    @Transactional
    public List<Object[]> getLineChartSeries(BaseEntity baseEntity, Date from, Date to) {
        return buildValuesQuery(em, "WorkspaceBroadcastEntity.fetchValues", baseEntity, from, to).getResultList();
    }

    @Override
    @Transactional
    public Object getPieChartSeries(BaseEntity source, Date from, Date to, WidgetPieChartEntity.PieChartValueType pieChartValueType) {
        switch (pieChartValueType) {
            case Count:
                return buildValuesQuery(em, "WorkspaceBroadcastEntity.fetchCount", source, from, to).getSingleResult();
        }
        throw new ServerException("Not implemented exception");
    }

    @Override
    public Date getMinDate(BaseEntity source) {
        throw new ServerException("Not implemented exception");
/*        return em.createNamedQuery("WorkspaceBackupEntity.fetchMinDate", Date.class)
                .setParameter("source", source).getSingleResult();*/
    }
}
