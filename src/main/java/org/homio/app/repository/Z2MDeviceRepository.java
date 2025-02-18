package org.homio.app.repository;

import org.homio.addon.z2m.model.Z2MDeviceEntity;
import org.homio.addon.z2m.model.Z2MLocalCoordinatorEntity;
import org.homio.addon.z2m.service.Z2MDeviceService;
import org.homio.api.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class Z2MDeviceRepository extends AbstractRepository<Z2MDeviceEntity> {

  @Lazy
  @Autowired
  private Context context;

  public Z2MDeviceRepository() {
    super(Z2MDeviceEntity.class);
  }

  @Override
  public @NotNull List<Z2MDeviceEntity> listAll() {
    List<Z2MDeviceEntity> list = new ArrayList<>();
    for (Z2MLocalCoordinatorEntity coordinator : context.db().findAll(Z2MLocalCoordinatorEntity.class)) {
      list.addAll(coordinator.getService().getDeviceHandlers().values().stream()
        .map(Z2MDeviceService::getDeviceEntity).toList());
    }
    return list;
  }

  @Override
  public Z2MDeviceEntity getByEntityID(String entityID) {
    for (Z2MLocalCoordinatorEntity coordinator : context.db().findAll(Z2MLocalCoordinatorEntity.class)) {
      for (Z2MDeviceService deviceService : coordinator.getService().getDeviceHandlers().values()) {
        if (deviceService.getDeviceEntity().getEntityID().equals(entityID)) {
          return deviceService.getDeviceEntity();
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull Z2MDeviceEntity save(@NotNull Z2MDeviceEntity entity) {
    // ignore
    return entity;
  }

  @Override
  public Z2MDeviceEntity getByEntityIDWithFetchLazy(@NotNull String entityID, boolean ignoreNotUILazy) {
    return getByEntityID(entityID);
  }

  @Override
  public boolean isUseCache() {
    return false;
  }
}
