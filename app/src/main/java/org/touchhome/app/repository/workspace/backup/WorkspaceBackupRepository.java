package org.touchhome.app.repository.workspace.backup;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.touchhome.app.repository.widget.HasFetchChartSeries;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.workspace.backup.WorkspaceBackupEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.Date;
import java.util.List;

@Repository("backupRepository")
public class WorkspaceBackupRepository extends AbstractRepository<WorkspaceBackupEntity> implements HasFetchChartSeries {

    public WorkspaceBackupRepository() {
        super(WorkspaceBackupEntity.class, WorkspaceBackupEntity.PREFIX);
    }

    @Override
    @Transactional
    public List<Object[]> getLineChartSeries(BaseEntity baseEntity, Date from, Date to) {
        return buildValuesQuery(em, "WorkspaceBackupEntity.fetchValues", baseEntity, from, to).getResultList();
    }

    @Transactional
    public Float getBackupLastValue(WorkspaceBackupEntity entity) {
        List list = em.createNamedQuery("WorkspaceBackupEntity.fetchLastValue").setParameter("source", entity).setMaxResults(1).getResultList();
        return list.isEmpty() ? 0 : (Float) list.get(0);
    }

    @Override
    @Transactional
    public Object getPieChartSeries(BaseEntity source, Date from, Date to, WidgetPieChartEntity.PieChartValueType pieChartValueType) {
        switch (pieChartValueType) {
            case Sum:
                return buildValuesQuery(em, "WorkspaceBackupEntity.fetchSum", source, from, to).getSingleResult();
            case Count:
                return buildValuesQuery(em, "WorkspaceBackupEntity.fetchCount", source, from, to).getSingleResult();
        }
        throw new RuntimeException("Not implemented exception");
    }

    @Override
    public Date getMinDate(BaseEntity source) {
        return null;
        //em.createNamedQuery("WorkspaceBackupValueCrudEntity.fetchMinDate", Date.class)
        //       .setParameter("source", source).getSingleResult();
    }
}



