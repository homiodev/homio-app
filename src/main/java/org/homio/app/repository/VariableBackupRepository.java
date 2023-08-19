package org.homio.app.repository;

import org.homio.app.config.TransactionManagerContext;
import org.homio.app.model.var.VariableBackup;
import org.homio.app.model.var.WorkspaceVariableMessage;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class VariableBackupRepository {

    private final TransactionManagerContext tmc;
    private final AtomicInteger nextId;

    public VariableBackupRepository(TransactionManagerContext tmc) {
        this.tmc = tmc;
        this.nextId = new AtomicInteger(getMaxId());
    }

    public Integer getMaxId() {
        String jql = "select max(id) from VariableBackup";
        return tmc.executeWithoutTransaction(em -> {
            Object result = em.createQuery(jql).getSingleResult();
            return result == null ? 0 : ((Number) result).intValue();
        });
    }

    public List<VariableBackup> findAll(String variableId, int limit) {
        String jql = "select vd from VariableBackup as vd where vd.vid = :vid order by vd.created desc";
        return tmc.executeInTransactionReadOnly(em ->
                em.createQuery(jql).setMaxResults(limit)
                        .setParameter("vid", variableId)
                        .getResultList());
    }

    public int count(String variableId) {
        String jql = "SELECT COUNT(vd) FROM VariableBackup vd where vd.vid = :vid";
        return tmc.executeWithoutTransaction(em -> {
            Object result = em.createQuery(jql).setParameter("vid", variableId).getSingleResult();
            return ((Number) result).intValue();
        });
    }

    public void save(String variableId, List<WorkspaceVariableMessage> values) {
        tmc.executeInTransaction(em -> {
            for (WorkspaceVariableMessage message : values) {
                em.persist(new VariableBackup(nextId.incrementAndGet(), variableId, message));
            }
        });
    }

    public int delete(String variableId) {
        String jql = "delete from VariableBackup where vid = :vid";
        return tmc.executeInTransaction(em -> {
            return em.createQuery(jql).setParameter("vid", variableId).executeUpdate();
        });
    }

    public int deleteButKeepCount(String variableId, int countToKeep) {
        String jql = "delete from variable_backup where vid = :vid and id not in (select id from variable_backup order by created desc limit :limit)";
        return tmc.executeInTransaction(em -> {
            return em.createNativeQuery(jql)
                    .setParameter("vid", variableId)
                    .setParameter("limit", countToKeep).executeUpdate();
        });
    }

    public int deleteButKeepDays(String variableId, int days) {
        LocalDate date = LocalDate.now().minusDays(days);
        String jql = "delete from VariableBackup where vid = :vid and created < :date";
        return tmc.executeInTransaction(em -> {
            return em.createQuery(jql)
                    .setParameter("vid", variableId)
                    .setParameter("date", date.toEpochDay()).executeUpdate();
        });
    }
}
