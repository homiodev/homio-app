package org.homio.app.manager.common.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.homio.addon.z2m.model.Z2MLocalCoordinatorEntity;
import org.homio.api.ContextSetting;
import org.homio.api.ContextSetting.MemSetterHandler;
import org.homio.api.ContextStorage;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.HasPlace;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceContract;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Status;
import org.homio.api.storage.DataStorageEntity;
import org.homio.api.storage.DataStorageService;
import org.homio.api.util.CommonUtils;
import org.homio.app.LogService;
import org.homio.app.config.TransactionManagerContext;
import org.homio.app.manager.CacheService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.ContextImpl.ItemAction;
import org.homio.app.manager.common.EntityManager;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasPosition;
import org.homio.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.homio.app.repository.AbstractRepository;
import org.homio.app.repository.device.AllDeviceRepository;
import org.homio.app.repository.widget.WidgetRepository;
import org.homio.app.repository.widget.WidgetSeriesRepository;
import org.homio.app.service.mem.InMemoryDB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Log4j2
@RequiredArgsConstructor
public class ContextStorageImpl implements ContextStorage {

    public static final Map<String, EntityMemoryData> ENTITY_MEMORY_MAP = new ConcurrentHashMap<>();
    private final @Getter
    @Accessors(fluent = true) ContextImpl context;
    private final TransactionManagerContext transactionManagerContext;
    private final AllDeviceRepository allDeviceRepository;
    private final EntityManager entityManager;
    private final CacheService cacheService;
    private final WidgetRepository widgetRepository;
    private final WidgetSeriesRepository widgetSeriesRepository;

    {
        ContextSetting.MEM_HANDLER.set(new MemSetterHandler() {
            @Override
            public void setValue(@NotNull HasEntityIdentifier entity, @NotNull String key, @NotNull String title, @Nullable Object value) {
                setMemValue(entity, key, title, value);
            }

            @Override
            public Object getValue(@NotNull HasEntityIdentifier entity, @NotNull String key, Object defaultValue) {
                EntityMemoryData data = ENTITY_MEMORY_MAP.computeIfAbsent(entity.getEntityID(), s -> new EntityMemoryData());
                return data.VALUE_MAP.getOrDefault(key, defaultValue);
            }
        });
    }

    @NotNull
    private static BaseEntity buildInitialCopyEntity(BaseEntity entity) {
        BaseEntity newEntity = CommonUtils.newInstance(entity.getClass());
        newEntity.setName(entity.getName());

        if (entity instanceof HasJsonData entityData) {
            HasJsonData newEntityData = (HasJsonData) newEntity;
            for (String key : entityData.getJsonData().keySet()) {
                newEntityData.setJsonData(key, entityData.getJsonData().get(key));
            }
        }

        if (entity instanceof WidgetEntity widgetEntity) {
            WidgetEntity newWidgetData = (WidgetEntity) newEntity;
            newWidgetData.setWidgetTabEntity(widgetEntity.getWidgetTabEntity());
        }

        if (entity instanceof DeviceBaseEntity deviceEntity) {
            DeviceBaseEntity newDeviceData = (DeviceBaseEntity) newEntity;
            newDeviceData.setIeeeAddress(deviceEntity.getIeeeAddress());
            newDeviceData.setImageIdentifier(deviceEntity.getImageIdentifier());
        }
        if (entity instanceof HasPlace placeEntity) {
            HasPlace newDeviceData = (HasPlace) newEntity;
            newDeviceData.setPlace(placeEntity.getPlace());
        }
        return newEntity;
    }

    @Override
    public <T extends BaseEntity> void createDelayed(@NotNull T entity) {
        putToCache(entity, null);
    }

    @SneakyThrows
    @Override
    public <T extends BaseEntity> void updateDelayed(T entity, Consumer<T> consumer) {
        if (entity.isDisableEdit()) {
            throw new IllegalAccessException("User is unable to update entity");
        }
        Map<String, Object[]> changeFields = new HashMap<>();
        MethodInterceptor handler = (obj, method, args, proxy) -> {
            String setName = method.getName();
            if (setName.startsWith("set")) {
                Object oldValue;
                try {
                    oldValue = context.getCacheService().getFieldValue(entity.getIdentifier(), setName);
                    if (oldValue == null) {
                        oldValue = MethodUtils.invokeMethod(entity, method.getName().replaceFirst("set", "get"));
                    }
                } catch (NoSuchMethodException ex) {
                    oldValue = MethodUtils.invokeMethod(entity, method.getName().replaceFirst("set", "is"));
                }
                Object newValue = setName.startsWith("setJsonData") ? args[1] : args[0];
                if (!Objects.equals(oldValue, newValue)) {
                    changeFields.put(setName, args);
                    // invoke called entity to update with new value
                    MethodUtils.invokeMethod(entity, method.getName(), args);
                }
            }
            if (method.getReturnType().isAssignableFrom(entity.getClass())) {
                proxy.invoke(entity, args);
                return obj;
            }
            return proxy.invoke(entity, args);
        };

        T proxyInstance = (T) Enhancer.create(entity.getClass(), handler);
        consumer.accept(proxyInstance);

        // fire entityUpdateListeners only if method called not from transaction
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            BaseEntity oldEntity = entityManager.getEntityNoCache(entity.getEntityID());
            runUpdateNotifyListeners(entity, oldEntity, context.event().getEntityUpdateListeners());
        }

        if (!changeFields.isEmpty()) {
            putToCache(entity, changeFields);

            // fire change event manually
            context.sendEntityUpdateNotification(entity, ItemAction.Update);
        }
    }

    @SneakyThrows
    @Override
    public <T extends BaseEntity> @NotNull T save(@NotNull T entity, boolean fireNotifyListeners) {
        AbstractRepository repository = ContextImpl.getRepository(entity.getEntityPrefix());
        ContextEventImpl.EntityListener entityUpdateListeners = context.event().getEntityUpdateListeners();

        String entityID = entity.getEntityID();
        entity.setContext(context);
        UserGuestEntity.assertEditAction(context, entity);
        if (entity.isDisableEdit()) {
            throw new IllegalAccessException("User is unable to persist/update entity");
        }
        if (entityID == null) {
            if (StringUtils.isEmpty(entity.getName())) {
                entity.setName(entity.refreshName());
            }
            entity.beforePersist();
        } else {
            entity.beforeUpdate();
        }

        entity.validate();
        T oldEntity = entityID == null ? null : get(entityID, false);

        T updatedEntity = transactionManagerContext.executeInTransaction(entityManager -> (T) repository.save(entity));

        if (fireNotifyListeners) {
            if (oldEntity == null) {
                runUpdateNotifyListeners(updatedEntity, null, entityUpdateListeners, context.event().getEntityCreateListeners());
            } else {
                runUpdateNotifyListeners(updatedEntity, oldEntity, entityUpdateListeners);
            }
        }

        if (StringUtils.isEmpty(entity.getEntityID())) {
            entity.setEntityID(updatedEntity.getEntityID());
        }

        // post save
        cacheService.entityUpdated(entity);

        return updatedEntity;
    }

    public List<BaseEntity> findAllBaseEntities() {
        return new ArrayList<>(findAll(DeviceBaseEntity.class));
    }

    @Override
    public <T extends BaseEntity> @NotNull List<T> findAll(@NotNull Class<T> clazz) {
        return findAllByRepository((Class<BaseEntity>) clazz);
    }

    @Override
    public <T extends BaseEntity> @NotNull List<T> findAllByPrefix(@NotNull String prefix) {
        AbstractRepository<? extends BaseEntity> repository = context.getRepositoryByPrefix(prefix);
        return findAllByRepository((Class<BaseEntity>) repository.getEntityClass());
    }

    @Override
    public BaseEntity delete(@NotNull String entityID) {
        AbstractRepository repository = ContextImpl.getRepository(entityID);
        BaseEntity deletedEntity = repository.deleteByEntityID(entityID);
        cacheService.clearCache();
        if (deletedEntity != null) {
            context.getBean(LogService.class).deleteEntityLogsFile(deletedEntity);
            runUpdateNotifyListeners(null, deletedEntity, context.event().getEntityRemoveListeners());
        }
        return deletedEntity;
    }

    @Override
    public <T extends BaseEntity> T get(@NotNull String entityID, boolean useCache) {
        T baseEntity = useCache ? entityManager.getEntityWithFetchLazy(entityID) : entityManager.getEntityNoCache(entityID);
        if (baseEntity == null) {
            baseEntity = entityManager.getEntityNoCache(entityID);
            if (baseEntity != null) {
                cacheService.clearCache();
            }
        }

        if (baseEntity != null) {
            cacheService.merge(baseEntity);
        }
        return baseEntity;
    }

    public BaseEntity copyEntity(BaseEntity entity) {
        BaseEntity newEntity = copyEntityItem(entity);
        BaseEntity saved = save(newEntity, false);

        // copy children if current entity is layout(it may contain children)
        if (entity instanceof WidgetLayoutEntity) {
            for (BaseEntity baseEntity : findAll(WidgetEntity.class)) {
                if (baseEntity instanceof HasPosition<?> positionEntity) {
                    if (entity.getEntityID().equals(positionEntity.getParent())) {
                        BaseEntity newChildEntity = copyEntityItem(baseEntity);
                        ((HasPosition) newChildEntity).setParent(saved.getEntityID());
                        save(newChildEntity, false);
                    }
                }
            }
        }

        saved = save(saved, true);
        cacheService.clearCache();
        return saved;
    }

    public void deleteInMemoryData(String entityID) {
        ENTITY_MEMORY_MAP.remove(entityID);
    }

    private void putToCache(BaseEntity entity, Map<String, Object[]> changeFields) {
        context.getCacheService().putToCache(ContextImpl.getRepository(entity.getEntityPrefix()), entity, changeFields);
    }

    private <T extends HasEntityIdentifier> void runUpdateNotifyListeners(@Nullable T updatedEntity, T oldEntity,
                                                                          ContextEventImpl.EntityListener... entityListeners) {
        if (updatedEntity != null || oldEntity != null) {
            context.bgp().builder("entity-" + (updatedEntity == null ? oldEntity : updatedEntity).getEntityID() + "-updated").hideOnUI(true)
                    .execute(() -> {
                        for (ContextEventImpl.EntityListener entityListener : entityListeners) {
                            entityListener.notify(updatedEntity, oldEntity);
                        }
                    });
        }
    }

    private BaseEntity copyEntityItem(BaseEntity entity) {
        BaseEntity newEntity = buildInitialCopyEntity(entity);
        if (newEntity instanceof WidgetEntity<?> widget) {
            widget.setParent(null);
        }

        // save to assign id
        newEntity = save(newEntity, false);

        if (entity instanceof WidgetEntityAndSeries widgetSeriesEntity) {
            WidgetEntityAndSeries newWidgetSeriesData = (WidgetEntityAndSeries) newEntity;
            Set<WidgetSeriesEntity> entitySeries = widgetSeriesEntity.getSeries();
            Set<WidgetSeriesEntity> series = new HashSet<>();
            for (WidgetSeriesEntity entry : entitySeries) {
                WidgetSeriesEntity seriesEntry = (WidgetSeriesEntity) buildInitialCopyEntity(entry);
                seriesEntry.setPriority(entry.getPriority());
                seriesEntry.setWidgetEntity((WidgetEntityAndSeries) newEntity);
                seriesEntry = save(seriesEntry, false);
                series.add(seriesEntry);
            }
            newWidgetSeriesData.setSeries(series);
        }
        return newEntity;
    }

    @Override
    public <T extends DataStorageEntity> DataStorageService<T> getOrCreateInMemoryService(@NotNull Class<T> pojoClass, @NotNull String uniqueId,
                                                                                          @Nullable Long quota) {
        return InMemoryDB.getOrCreateService(pojoClass, uniqueId, quota);
    }

    @Override
    public List<DeviceBaseEntity> getDeviceEntity(@NotNull String ieeeAddress, @Nullable String typePrefix) {
        List<DeviceBaseEntity> entities = findAll(DeviceBaseEntity.class);
        Stream<DeviceBaseEntity> stream = entities
                .stream()
                .filter(e -> ieeeAddress.equals(e.getIeeeAddress()) || e.getEntityID().equals(ieeeAddress));
        if (typePrefix != null) {
            stream = stream.filter(e -> e.getEntityID().startsWith(DeviceBaseEntity.PREFIX + typePrefix));
        }
        return stream.toList();
    }

    private void setMemValue(@NotNull HasEntityIdentifier entity, @NotNull String key, @NotNull String title, @Nullable Object value) {
        EntityMemoryData data = ENTITY_MEMORY_MAP.computeIfAbsent(entity.getEntityID(), s -> new EntityMemoryData());
        boolean sendUpdateToUI = entity instanceof HasStatusAndMsg && key.startsWith("status");
        if (value == null || (value instanceof String && value.toString().isEmpty())) {
            if (data.VALUE_MAP.remove(key) == null) {
                sendUpdateToUI = false;
            }
        } else {
            Object prevValue = data.VALUE_MAP.put(key, value);
            if (!Objects.equals(value, prevValue)) {
                logUpdatedValue(entity, key, title, value, data);
            } else {
                sendUpdateToUI = false;
            }
        }

        if (sendUpdateToUI) {
            context.ui().updateItem((BaseEntity) entity, key, value);
            if (key.equals("status") && entity instanceof DeviceContract device) {
                context.ui().updateItem((BaseEntity) entity, "entityStatus", device.getEntityStatus());
            }
        }
    }

    private void logUpdatedValue(@NotNull HasEntityIdentifier entity, @NotNull String key, @NotNull String title, @NotNull Object value,
                                 EntityMemoryData data) {
        if (value instanceof Status status) {
            Level level = status == Status.ERROR ? Level.ERROR : Level.DEBUG;
            Object message = data.VALUE_MAP.get(key + "Message");
            if (message == null) {
                LogManager.getLogger(entity.getClass()).log(level, "[{}]: Set {} '{}' status: {}", entity.getEntityID(), entity, title, status);
            } else {
                LogManager.getLogger(entity.getClass())
                        .log(level, "[{}]: Set {} '{}' status: {}. Msg: {}", entity.getEntityID(), entity, title, status, message);
            }
        }

        if (entity instanceof BaseEntity) {
            runUpdateNotifyListeners(entity, null, context.event().getEntityUpdateStatusListeners());
        }
    }

    private <T extends BaseEntity> List<T> findAllByRepository(Class<BaseEntity> clazz) {
        AbstractRepository repository;
        if (Modifier.isAbstract(clazz.getModifiers())) {
            if (DeviceBaseEntity.class.isAssignableFrom(clazz)) {
                repository = allDeviceRepository;
            } else if (WidgetEntity.class.isAssignableFrom(clazz)) {
                repository = widgetRepository;
            } else if (WidgetSeriesEntity.class.isAssignableFrom(clazz)) {
                repository = widgetSeriesRepository;
            } else {
                throw new IllegalStateException("Unable to find repository for class: " + clazz.getSimpleName());
            }

        } else {
            repository = ContextImpl.getRepository(CommonUtils.newInstance(clazz).getEntityPrefix());
        }
        List<T> result = new ArrayList<>();
        if (!repository.isUseCache()) {
            result.addAll(repository.listAll());
        } else {
            result.addAll(entityManager.getEntityIDsByEntityClassFullName(clazz, repository).stream().map(entityID ->
                    entityManager.<T>getEntityWithFetchLazy(entityID)).filter(Objects::nonNull).toList());
        }
        if (clazz.equals(DeviceBaseEntity.class)) {
            List<T> memoryDevices = new ArrayList<>();
            for (T t : result) {
                if (t instanceof Z2MLocalCoordinatorEntity coordinator) {
                    memoryDevices.addAll(coordinator.getService().getDeviceHandlers().values().stream()
                            .map(z2MDeviceService -> (T) z2MDeviceService.getDeviceEntity()).toList());
                }
            }
            result.addAll(memoryDevices);
        }
        return result;
    }

    private static class EntityMemoryData {

        private final Map<String, Object> VALUE_MAP = new ConcurrentHashMap<>();
    }
}
