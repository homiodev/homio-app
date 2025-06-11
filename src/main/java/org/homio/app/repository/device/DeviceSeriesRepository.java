package org.homio.app.repository.device;

import lombok.extern.log4j.Log4j2;
import org.homio.api.entity.device.DeviceSeriesEntity;
import org.homio.app.repository.AbstractRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import java.util.List;

@Log4j2
@Repository
public class DeviceSeriesRepository extends AbstractRepository<DeviceSeriesEntity> {

  public DeviceSeriesRepository() {
    super(DeviceSeriesEntity.class, "devser_");
  }

  @Override
  public DeviceSeriesEntity getByEntityID(String entityID) {
    return super.getByEntityID(entityID);
  }

  @Override
  public @NotNull List listAll() {
    return super.listAll();
  }
}
