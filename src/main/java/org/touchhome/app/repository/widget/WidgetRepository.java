package org.touchhome.app.repository.widget;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.List;

@Log4j2
@Repository
public class WidgetRepository extends AbstractRepository<WidgetBaseEntity> {

    public WidgetRepository() {
        super(WidgetBaseEntity.class);
    }

    @Override
    @Transactional(readOnly = true)
    public WidgetBaseEntity getByEntityID(String entityID) {
        return super.getByEntityID(entityID);
    }

    @Override
    @Transactional(readOnly = true)
    public List listAll() {
        return super.listAll();
    }
}
