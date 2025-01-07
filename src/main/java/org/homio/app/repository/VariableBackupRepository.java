package org.homio.app.repository;

import org.homio.app.config.TransactionManagerContext;
import org.homio.app.model.var.VariableBackup;
import org.homio.app.model.var.WorkspaceVariable;
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

  public List<VariableBackup> findAll(WorkspaceVariable variable, int limit) {
    String jql = "from VariableBackup where workspaceVariable = :vid order by created desc";
    return tmc.executeInTransactionReadOnly(em ->
      em.createQuery(jql).setMaxResults(limit)
        .setParameter("vid", variable)
        .getResultList());
  }

  public void save(WorkspaceVariable variable, WorkspaceVariableMessage message) {
    tmc.executeInTransaction(em -> {
      em.persist(new VariableBackup(nextId.incrementAndGet(), variable, message));
    });
  }

  public void save(WorkspaceVariable variable, List<WorkspaceVariableMessage> values) {
    tmc.executeInTransaction(em -> {
      for (WorkspaceVariableMessage message : values) {
        em.persist(new VariableBackup(nextId.incrementAndGet(), variable, message));
      }
    });
  }

  public int count(WorkspaceVariable variable) {
    String jql = "SELECT COUNT(*) FROM VariableBackup where workspaceVariable = :vid";
    return tmc.executeWithoutTransaction(em -> {
      Object result = em.createQuery(jql).setParameter("vid", variable).getSingleResult();
      return ((Number) result).intValue();
    });
  }

  public int delete(WorkspaceVariable variable) {
    String jql = "delete from VariableBackup where workspaceVariable = :vid";
    return tmc.executeInTransaction(em -> {
      return em.createQuery(jql).setParameter("vid", variable).executeUpdate();
    });
  }

  public int deleteButKeepCount(WorkspaceVariable variable, int countToKeep) {
    String jql = "delete from VariableBackup where vid = :vid and id not in (select id from VariableBackup order by created desc limit :limit)";
    return tmc.executeInTransaction(em -> {
      return em.createNativeQuery(jql)
        .setParameter("vid", variable)
        .setParameter("limit", countToKeep).executeUpdate();
    });
  }

  public int deleteButKeepDays(WorkspaceVariable variable, int days) {
    LocalDate date = LocalDate.now().minusDays(days);
    String jql = "delete from VariableBackup where workspaceVariable = :vid and created < :date";
    return tmc.executeInTransaction(em -> {
      return em.createQuery(jql)
        .setParameter("vid", variable)
        .setParameter("date", date.toEpochDay()).executeUpdate();
    });
  }
}
