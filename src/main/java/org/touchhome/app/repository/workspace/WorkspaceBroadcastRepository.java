package org.touchhome.app.repository.workspace;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.Date;
import java.util.List;

import static org.touchhome.bundle.api.entity.widget.HasLineChartSeries.buildValuesQuery;

@Repository("broadcastRepository")
public class WorkspaceBroadcastRepository extends AbstractRepository<WorkspaceBroadcastEntity> {

    public WorkspaceBroadcastRepository() {
        super(WorkspaceBroadcastEntity.class);
    }

    @Transactional
    public List<Object[]> getLineChartSeries(BaseEntity baseEntity, Date from, Date to) {
        return buildValuesQuery(em, "WorkspaceBroadcastEntity.fetchValues", baseEntity, from, to).getResultList();
    }

    @Transactional
    public double getPieSumChartSeries(WorkspaceBroadcastEntity workspaceBroadcastEntity, Date from, Date to) {
        return -1;
    }

    @Transactional
    public double getPieCountChartSeries(WorkspaceBroadcastEntity workspaceBroadcastEntity, Date from, Date to) {
        return (double) buildValuesQuery(em, "WorkspaceBroadcastEntity.fetchCount", workspaceBroadcastEntity, from, to).getSingleResult();
    }

   /* @Override
    public Date getMinDate(BaseEntity source) {
        throw new ServerException("Not implemented exception");
*//*        return em.createNamedQuery("WorkspaceBackupEntity.fetchMinDate", Date.class)
                .setParameter("source", source).getSingleResult();*//*
    }*/
}
