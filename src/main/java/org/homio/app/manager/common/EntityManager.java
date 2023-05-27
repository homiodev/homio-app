package org.homio.app.manager.common;

import static org.homio.app.manager.CacheService.CACHE_CLASS_BY_TYPE;
import static org.homio.app.manager.CacheService.ENTITY_IDS_BY_CLASS_NAME;
import static org.homio.app.manager.CacheService.ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI;
import static org.homio.app.manager.CacheService.REPOSITORY_BY_ENTITY_ID;

import jakarta.persistence.Entity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.repository.AbstractRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class EntityManager {

    private final ClassFinder classFinder;

    <T extends BaseEntity> T getEntityNoCache(String entityID) {
        return (T)
                getRepositoryByEntityID(entityID).map(r -> r.getByEntityID(entityID)).orElse(null);
    }

    @Cacheable(ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI)
    public <T extends BaseEntity> T getEntityWithFetchLazy(String entityID) {
        return (T)
                getRepositoryByEntityID(entityID)
                        .map(r -> r.getByEntityIDWithFetchLazy(entityID, false))
                        .orElse(null);
    }

    @Cacheable(CACHE_CLASS_BY_TYPE)
    public Class<? extends EntityFieldMetadata> getUIFieldClassByType(String type) {
        for (Class<? extends EntityFieldMetadata> aClass : EntityContextImpl.uiFieldClasses.values()) {
            Entity entity = aClass.getDeclaredAnnotation(Entity.class);
            if (entity != null && entity.name().equals(type)
                || aClass.getName().equals(type)
                || aClass.getSimpleName().equals(type)) {
                return aClass;
            }
        }
        return null;
    }

    public BaseEntity<? extends BaseEntity> delete(String entityID) {
        return getRepositoryByEntityID(entityID)
                .map(r -> r.deleteByEntityID(entityID))
                .orElse(null);
    }

    @Cacheable(REPOSITORY_BY_ENTITY_ID)
    public Optional<AbstractRepository> getRepositoryByEntityID(String entityID) {
        if (entityID != null) {
            for (AbstractRepository abstractRepository : EntityContextImpl.repositories.values()) {
                if (abstractRepository.isMatch(entityID)) {
                    return Optional.of(abstractRepository);
                }
            }
        }
        // in case if entity has no 1-1 repository then try to find base repository if possible
        for (Map.Entry<String, AbstractRepository> entry :
                EntityContextImpl.repositoriesByPrefix.entrySet()) {
            if (entityID.startsWith(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    @SneakyThrows
    @Cacheable(ENTITY_IDS_BY_CLASS_NAME)
    public Set<String> getEntityIDsByEntityClassFullName(Class<BaseEntity> entityClass) {
        List<BaseEntity> list;
        Predicate<BaseEntity> filter = baseEntity -> true;
        AbstractRepository<BaseEntity> repositoryByClass =
                classFinder.getRepositoryByClass(entityClass);

        // in case we not found repository, but found potential repository - we should filter
        if (!repositoryByClass.getEntityClass().equals(entityClass)) {
            filter = baseEntity -> entityClass.isAssignableFrom(baseEntity.getClass());
        }

        list = repositoryByClass.listAll();
        return list.stream()
                .filter(filter)
                .map(BaseEntity::getEntityID)
                .collect(Collectors.toSet());
    }
}
