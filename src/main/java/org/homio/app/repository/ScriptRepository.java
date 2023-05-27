package org.homio.app.repository;

import org.homio.api.repository.AbstractRepository;
import org.homio.app.model.entity.ScriptEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository("executeRepository")
public class ScriptRepository extends AbstractRepository<ScriptEntity> {

    public ScriptRepository() {
        super(ScriptEntity.class);
    }

    @Transactional(readOnly = true)
    public ScriptEntity getByURL(String url) {
        return findSingleByField("url", url);
    }
}
