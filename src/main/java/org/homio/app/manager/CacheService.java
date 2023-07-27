package org.homio.app.manager;

import static org.homio.app.manager.common.ClassFinder.CLASSES_WITH_PARENT_CLASS;
import static org.homio.app.manager.common.ClassFinder.REPOSITORY_BY_CLAZZ;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.homio.api.EntityContext;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.ServerException;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.app.repository.AbstractRepository;
import org.homio.app.utils.CollectionUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CacheService {

    public static final String CACHE_CLASS_BY_TYPE = "CACHE_CLASS_BY_TYPE";
    public static final String ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI =
        "ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI";
    public static final String ENTITY_IDS_BY_CLASS_NAME = "ENTITY_IDS_BY_CLASS_NAME";
    public static final String JS_COMPLETIONS = "JS_COMPLETIONS";

    private final Map<String, UpdateStatement> entityCache = new ConcurrentHashMap<>();

    private final CacheManager cacheManager;
    private final ApplicationContext applicationContext;

    public static CacheManager createCacheManager() {
        return new ConcurrentMapCacheManager(
            CLASSES_WITH_PARENT_CLASS,
            ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI,
            ENTITY_IDS_BY_CLASS_NAME,
            REPOSITORY_BY_CLAZZ,
            CACHE_CLASS_BY_TYPE,
            JS_COMPLETIONS);
    }

    public void clearCache() {
        log.info("Clear cache");
        for (String cache : cacheManager.getCacheNames()) {
            Objects.requireNonNull(cacheManager.getCache(cache)).clear();
        }
    }

    public void entityUpdated(BaseEntity entity) {
        Set<BaseEntity> relatedEntities = CollectionUtils.nullSafeSet();
        entity.getAllRelatedEntities(relatedEntities);
        relatedEntities.add(entity);
        for (BaseEntity relatedEntity : relatedEntities) {
            if (relatedEntity != null) {
                Objects.requireNonNull(cacheManager.getCache(ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI))
                       .evict(relatedEntity.getEntityID());
            }
        }
        // need remove all because entity may create also another entities
        Objects.requireNonNull(cacheManager.getCache(ENTITY_IDS_BY_CLASS_NAME)).clear();
    }

    public void putToCache(AbstractRepository repository, HasEntityIdentifier entity, Map<String, Object[]> changeFields) {
        String identifier = entity.getIdentifier();
        if (identifier == null) {
            throw new ServerException("Unable update state without id" + entity);
        }
        synchronized (entityCache) {
            if (entityCache.containsKey(identifier)) {
                // override changed fields
                entityCache.get(identifier).changeFields.putAll(changeFields);
            } else {
                entityCache.put(identifier, new UpdateStatement(identifier, repository, changeFields));
            }
        }
    }

    @SneakyThrows
    public void merge(BaseEntity baseEntity) {
        UpdateStatement updateStatement = entityCache.get(baseEntity.getIdentifier());
        if (updateStatement != null && updateStatement.changeFields != null) {
            for (Map.Entry<String, Object[]> entry : updateStatement.changeFields.entrySet()) {
                MethodUtils.invokeMethod(baseEntity, entry.getKey(), entry.getValue());
            }
        }
    }

    public void delete(String entityId) {
        entityCache.remove(entityId);
    }

    @Scheduled(fixedDelay = 30000)
    public void flushDelayedUpdates() {
        if (!entityCache.isEmpty()) {
            synchronized (entityCache) {
                EntityContext entityContext = applicationContext.getBean(EntityContext.class);
                for (UpdateStatement updateStatement : entityCache.values()) {
                    try {
                        if (updateStatement.changeFields != null) {
                            BaseEntity baseEntity = entityContext.getEntity(updateStatement.entityID, false);
                            for (Map.Entry<String, Object[]> entry : updateStatement.changeFields.entrySet()) {
                                MethodUtils.invokeMethod(baseEntity, entry.getKey(), entry.getValue());
                            }
                            updateStatement.repository.flushCashedEntity(baseEntity);

                            if (baseEntity instanceof BaseEntity) {
                                entityUpdated((BaseEntity) baseEntity);
                            }
                        }
                    } catch (Exception ex) {
                        log.error("Error delay update entity <{}>", updateStatement.entityID, ex);
                    }
                }
                entityCache.clear();
            }
        }
    }

    public Object getFieldValue(String identifier, String key) {
        synchronized (entityCache) {
            if (entityCache.containsKey(identifier)) {
                return entityCache.get(identifier).changeFields.get(key);
            }
        }
        return null;
    }

    @AllArgsConstructor
    private static class UpdateStatement {

        String entityID;
        AbstractRepository repository;
        Map<String, Object[]> changeFields;
    }
}
