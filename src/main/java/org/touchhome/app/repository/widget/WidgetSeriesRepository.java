package org.touchhome.app.repository.widget;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.List;

@Log4j2
@Repository
public class WidgetSeriesRepository extends AbstractRepository<WidgetSeriesEntity> {

    public WidgetSeriesRepository() {
        super(WidgetSeriesEntity.class);
    }

    @Override
    @Transactional(readOnly = true)
    public WidgetSeriesEntity getByEntityID(String entityID) {
        return super.getByEntityID(entityID);
    }

    @Override
    @Transactional(readOnly = true)
    public List listAll() {
        return super.listAll();
    }
}
