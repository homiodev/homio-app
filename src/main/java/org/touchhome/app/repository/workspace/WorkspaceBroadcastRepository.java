package org.touchhome.app.repository.workspace;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.ChartRequest;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.List;

@Repository("broadcastRepository")
public class WorkspaceBroadcastRepository extends AbstractRepository<WorkspaceBroadcastEntity> {

    public WorkspaceBroadcastRepository() {
        super(WorkspaceBroadcastEntity.class);
    }

    @Transactional
    public List<Object[]> getLineChartSeries(BaseEntity source, ChartRequest request) {
        //noinspection unchecked
        return (List<Object[]>) queryForValues("creationTime", source, request, "ORDER BY e.creationTime");
    }

    public Float getValue(WorkspaceBroadcastEntity source, ChartRequest request) {
        return findExactOneBackupValue("COUNT(id)", source, request);
    }

    private Float findExactOneBackupValue(String select, BaseEntity entity, ChartRequest request) {
        return findExactOneBackupValue(select, entity, request, "");
    }

    private Float findExactOneBackupValue(String select, BaseEntity entity, ChartRequest request, String sort) {
        List<?> list = queryForValues(select, entity, request, sort);
        return list.isEmpty() ? 0F : (Float) list.get(0);
    }

    private List<?> queryForValues(String select, BaseEntity entity, ChartRequest request, String sort) {
        return (List<?>) em.createQuery("SELECT " + select + " FROM WorkspaceBroadcastValueCrudEntity " +
                        "where workspaceBroadcastEntity = :source and creationTime >= :from and creationTime <= :to" + sort)
                .setParameter("source", entity)
                .setParameter("from", request.getFrom())
                .setParameter("to", request.getTo())
                .getResultList();
    }
}
