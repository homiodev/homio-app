package org.homio.app.repository.widget;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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