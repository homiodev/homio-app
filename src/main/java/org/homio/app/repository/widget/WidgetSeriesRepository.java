package org.homio.app.repository.widget;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.homio.api.repository.AbstractRepository;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
public class WidgetSeriesRepository extends AbstractRepository<WidgetSeriesEntity> {

    public WidgetSeriesRepository() {
        super(WidgetSeriesEntity.class);
    }

    @Override
    public WidgetSeriesEntity getByEntityID(String entityID) {
        return super.getByEntityID(entityID);
    }

    @Override
    public List listAll() {
        return super.listAll();
    }
}
