package org.homio.app.manager.common;

import static org.homio.app.manager.CacheService.CACHE_CLASS_BY_TYPE;
import static org.homio.app.manager.CacheService.ENTITY_IDS_BY_CLASS_NAME;
import static org.homio.app.manager.CacheService.ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI;

import jakarta.persistence.Entity;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.app.repository.AbstractRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class EntityManager {

    @Cacheable(ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI)
    public <T extends BaseEntity> T getEntityWithFetchLazy(String entityID) {
        AbstractRepository repository = ContextImpl.getRepository(entityID);
        return (T) repository.getByEntityIDWithFetchLazy(entityID, false);
    }

    @Cacheable(CACHE_CLASS_BY_TYPE)
    public Class<? extends EntityFieldMetadata> getUIFieldClassByType(String type) {
        for (Class<? extends EntityFieldMetadata> aClass : ContextImpl.uiFieldClasses.values()) {
            Entity entity = aClass.getDeclaredAnnotation(Entity.class);
            if (entity != null && entity.name().equals(type)
                    || aClass.getName().equals(type)
                    || aClass.getSimpleName().equals(type)) {
                return aClass;
            }
        }
        return null;
    }

    @SneakyThrows
    @Cacheable(ENTITY_IDS_BY_CLASS_NAME)
    public @NotNull Set<String> getEntityIDsByEntityClassFullName(Class<BaseEntity> entityClass, AbstractRepository repository) {
        Predicate<BaseEntity> filter = baseEntity -> true;

        // in case we not found repository, but found potential repository - we should filter
        if (!repository.getEntityClass().equals(entityClass)) {
            filter = baseEntity -> entityClass.isAssignableFrom(baseEntity.getClass());
        }

        List<BaseEntity> list = repository.listAll();
        return list.stream()
                .filter(filter)
                .map(BaseEntity::getEntityID)
                .collect(Collectors.toSet());
    }

    public <T extends BaseEntity> @Nullable T getEntityNoCache(String entityID) {
        return (T) ContextImpl.getRepository(entityID).getByEntityID(entityID);
    }
}
