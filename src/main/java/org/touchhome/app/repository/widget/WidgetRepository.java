package org.touchhome.app.repository.widget;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.model.entity.widget.impl.WidgetBaseEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.List;

@Log4j2
@Repository
public class WidgetRepository extends AbstractRepository<WidgetBaseEntity> {

    private EntityContext entityContext;

    public WidgetRepository(EntityContext entityContext) {
        super(WidgetBaseEntity.class, "ow_");
        this.entityContext = entityContext;
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

    @Override
    public WidgetBaseEntity save(WidgetBaseEntity entity) {
        if (entity.getWidgetTabEntity() == null) {
            throw new IllegalStateException("Unable to save widget without attach to tab");
        }
        return super.save(entity);
    }

    @Override
    public void updateEntityAfterFetch(WidgetBaseEntity widgetBaseEntity) {
        if (widgetBaseEntity.updateRelations(entityContext)) {
            save(widgetBaseEntity);
        }
    }
}
