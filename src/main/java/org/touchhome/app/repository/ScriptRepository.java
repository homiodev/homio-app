package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

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

















