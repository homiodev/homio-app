package org.homio.app.repository;

import org.homio.api.repository.AbstractRepository;
import org.homio.app.model.entity.ScriptEntity;
import org.springframework.stereotype.Repository;

@Repository("executeRepository")
public class ScriptRepository extends AbstractRepository<ScriptEntity> {

    public ScriptRepository() {
        super(ScriptEntity.class);
    }

    public ScriptEntity getByURL(String url) {
        return tm.executeInTransaction(entityManager -> {
            return findSingle(entityManager, "url", url);
        });
    }
}
