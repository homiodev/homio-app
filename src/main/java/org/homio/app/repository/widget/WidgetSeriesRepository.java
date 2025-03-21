package org.homio.app.repository.widget;

import lombok.extern.log4j.Log4j2;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.repository.AbstractRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import java.util.List;

@Log4j2
@Repository
public class WidgetSeriesRepository extends AbstractRepository<WidgetSeriesEntity> {

  public WidgetSeriesRepository() {
    super(WidgetSeriesEntity.class, "series_");
  }

  @Override
  public WidgetSeriesEntity getByEntityID(String entityID) {
    return super.getByEntityID(entityID);
  }

  @Override
  public @NotNull List listAll() {
    return super.listAll();
  }
}
