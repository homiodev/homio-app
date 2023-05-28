package org.homio.app.repository;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.homio.api.repository.EntityManagerContext;
import org.homio.app.model.var.VariableBackup;
import org.homio.app.model.var.WorkspaceVariableMessage;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VariableDataRepository {

    private final EntityManagerContext emc;

    public List<VariableBackup> findAll(String variableId, int limit) {
        String jql = "select vd from VariableBackup as vd where vd.vid = :vid order by vd.created desc";
        return emc.executeInTransaction(entityManager ->
            (List<VariableBackup>) entityManager
                .createQuery(jql).setMaxResults(limit)
                .setParameter("vid", variableId)
                .getResultList());
    }

    public int count(String variableId) {
        return emc.executeInTransaction(entityManager -> {
            String jql = "SELECT COUNT(vd) FROM VariableBackup vd where vd.vid = :vid";
            Object result = entityManager.createQuery(jql).setParameter("vid", variableId).getSingleResult();
            return ((Number) result).intValue();
        });
    }

    public void save(String variableId, List<WorkspaceVariableMessage> values) {
        emc.executeInTransaction(entityManager -> {
            for (WorkspaceVariableMessage message : values) {
                entityManager.persist(new VariableBackup(variableId, message));
            }
        });
    }

    public int delete(String variableId) {
        String jql = "delete from VariableBackup where vid = :vid";
        return emc.executeInTransaction(entityManager -> {
            return entityManager.createQuery(jql).setParameter("vid", variableId).executeUpdate();
        });
    }

    public int deleteButKeepCount(String variableId, int countToKeep) {
        String jql = "delete from variable_backup where vid = :vid and id not in (select id from variable_backup order by created desc limit :limit)";
        return emc.executeInTransaction(entityManager -> {
            return entityManager.createNativeQuery(jql)
                                .setParameter("vid", variableId)
                                .setParameter("limit", countToKeep).executeUpdate();
        });
    }

    public int deleteButKeepDays(String variableId, int days) {
        LocalDate date = LocalDate.now().minusDays(days);
        String jql = "delete from VariableBackup where vid = :vid and created < :date";
        return emc.executeInTransaction(entityManager -> {
            return entityManager.createQuery(jql)
                                .setParameter("vid", variableId)
                                .setParameter("date", date.toEpochDay()).executeUpdate();
        });
    }
}
