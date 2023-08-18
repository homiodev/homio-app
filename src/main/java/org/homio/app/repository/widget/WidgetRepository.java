package org.homio.app.repository.widget;

import java.util.List;

import lombok.extern.log4j.Log4j2;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.repository.AbstractRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class WidgetRepository extends AbstractRepository<WidgetBaseEntity> {

    public WidgetRepository() {
        super(WidgetBaseEntity.class, "widget_");
    }

    @Override
    public WidgetBaseEntity getByEntityID(String entityID) {
        return super.getByEntityID(entityID);
    }

    @Override
    public @NotNull List listAll() {
        return super.listAll();
    }
}
