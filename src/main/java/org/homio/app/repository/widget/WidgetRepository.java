package org.homio.app.repository.widget;

import lombok.extern.log4j.Log4j2;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.repository.AbstractRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import java.util.List;

@Log4j2
@Repository
public class WidgetRepository extends AbstractRepository<WidgetEntity> {

  public WidgetRepository() {
    super(WidgetEntity.class, "widget_");
  }

  @Override
  public WidgetEntity getByEntityID(String entityID) {
    return super.getByEntityID(entityID);
  }

  @Override
  public @NotNull List listAll() {
    return super.listAll();
  }
}
