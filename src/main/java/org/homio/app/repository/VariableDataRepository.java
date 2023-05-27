package org.homio.app.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.homio.app.model.var.VariableBackup;
import org.homio.app.model.var.WorkspaceVariableMessage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class VariableDataRepository {

    @PersistenceContext
    protected EntityManager em;

    public List<VariableBackup> findAll(String variableId, int limit) {
        String jql = "select vd from VariableBackup as vd where vd.vid = :vid order by vd.created desc";
        List<VariableBackup> data = em.createQuery(jql).setMaxResults(limit)
                                      .setParameter("vid", variableId)
                                      .getResultList();
        return data;
    }

    public int count(String variableId) {
        String jql = "SELECT COUNT(vd) FROM VariableBackup vd where vd.vid = :vid";
        Object result = em.createQuery(jql).setParameter("vid", variableId).getSingleResult();
        return ((Number) result).intValue();
    }

    @Transactional
    public void save(String variableId, List<WorkspaceVariableMessage> values) {
        for (WorkspaceVariableMessage message : values) {
            em.persist(new VariableBackup(variableId, message));
        }
    }

    @Transactional
    public int delete(String variableId) {
        String jql = "delete from VariableBackup where vid = :vid";
        return em.createQuery(jql).setParameter("vid", variableId).executeUpdate();
    }

    @Transactional
    public int deleteButKeepCount(String variableId, int countToKeep) {
        String jql = "delete from variable_backup where vid = :vid and id not in (select id from variable_backup order by created desc limit :limit)";
        return em.createNativeQuery(jql)
                 .setParameter("vid", variableId)
                 .setParameter("limit", countToKeep).executeUpdate();
    }

    @Transactional
    public int deleteButKeepDays(String variableId, int days) {
        LocalDate date = LocalDate.now().minusDays(days);

        String jql = "delete from VariableBackup where vid = :vid and created < :date";
        return em.createQuery(jql)
                 .setParameter("vid", variableId)
                 .setParameter("date", date.toEpochDay()).executeUpdate();
    }
}
