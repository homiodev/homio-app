package org.homio.app.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceVariableRepository extends AbstractRepository<WorkspaceVariable> {

    public WorkspaceVariableRepository() {
        super(WorkspaceVariable.class);
    }

    public void deleteAll() {
        for (WorkspaceVariable workspaceVariable : listAll()) {
            deleteByEntityID(workspaceVariable.getEntityID());
        }
    }

    public Integer unlockVariablesByGroup(WorkspaceGroup group) {
        return tmc.executeInTransaction(em -> {
            CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
            CriteriaUpdate<WorkspaceVariable> updateCriteria = criteriaBuilder.createCriteriaUpdate(WorkspaceVariable.class);
            Root<WorkspaceVariable> entityRoot = updateCriteria.from(WorkspaceVariable.class);
            updateCriteria.set(entityRoot.get("locked"), false);
            updateCriteria.where(criteriaBuilder.equal(entityRoot.get("workspaceGroup"), group));
            return em.createQuery(updateCriteria).executeUpdate();
        });
    }
}
