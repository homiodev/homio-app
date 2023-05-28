package org.homio.app.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.homio.app.model.var.VariableBackup;
import org.homio.app.model.var.WorkspaceVariableMessage;
import org.springframework.stereotype.Repository;

@Repository
public class VariableDataRepository {

   // private final TransactionManager tm;

    public List<VariableBackup> findAll(String variableId, int limit) {
        /*String jql = "select vd from VariableBackup as vd where vd.vid = :vid order by vd.created desc";
        return (List<VariableBackup>) tm.getEntityManager()
                                        .createQuery(jql).setMaxResults(limit)
                                        .setParameter("vid", variableId)
                                        .getResultList();*/
        return null;
    }

    public int count(String variableId) {
       /* String jql = "SELECT COUNT(vd) FROM VariableBackup vd where vd.vid = :vid";
        Object result = tm.getEntityManager().createQuery(jql).setParameter("vid", variableId).getSingleResult();
        return ((Number) result).intValue();*/
        return 0;
    }

    public void save(String variableId, List<WorkspaceVariableMessage> values) {
        /*tm.executeInTransaction(entityManager -> {
            for (WorkspaceVariableMessage message : values) {
                entityManager.persist(new VariableBackup(variableId, message));
            }
        });*/
    }

    public int delete(String variableId) {
        /*String jql = "delete from VariableBackup where vid = :vid";
        return tm.executeInTransaction(entityManager -> {
            return entityManager.createQuery(jql).setParameter("vid", variableId).executeUpdate();
        });*/
        return 0;
    }

    public int deleteButKeepCount(String variableId, int countToKeep) {
        /*String jql = "delete from variable_backup where vid = :vid and id not in (select id from variable_backup order by created desc limit :limit)";
        return tm.executeInTransaction(entityManager -> {
            return entityManager.createNativeQuery(jql)
                                .setParameter("vid", variableId)
                                .setParameter("limit", countToKeep).executeUpdate();
        });*/
        return 0;
    }

    public int deleteButKeepDays(String variableId, int days) {
        /*LocalDate date = LocalDate.now().minusDays(days);
        String jql = "delete from VariableBackup where vid = :vid and created < :date";
        return tm.executeInTransaction(entityManager -> {
            return entityManager.createQuery(jql)
                                .setParameter("vid", variableId)
                                .setParameter("date", date.toEpochDay()).executeUpdate();
        });*/
        return 0;
    }
}
