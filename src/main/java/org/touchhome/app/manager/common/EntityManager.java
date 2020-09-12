package org.touchhome.app.manager.common;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import javax.persistence.Entity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.touchhome.app.manager.CacheService.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class EntityManager {

    private final ClassFinder classFinder;

    <T extends BaseEntity> T getEntityNoCache(String entityID) {
        return (T) getRepositoryByEntityID(entityID)
                .map(r -> r.getByEntityID(entityID))
                .orElse(null);
    }

    @Cacheable(ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI)
    public <T extends BaseEntity> T getEntityWithFetchLazy(String entityID) {
        return (T) getRepositoryByEntityID(entityID)
                .map(r -> r.getByEntityIDWithFetchLazy(entityID, false))
                .orElse(null);
    }

    @Cacheable(CACHE_CLASS_BY_TYPE)
    public Class<? extends BaseEntity> getClassByType(String type) {
        for (Class<? extends BaseEntity> aClass : EntityContextImpl.baseEntityNameToClass.values()) {
            Entity entity = aClass.getDeclaredAnnotation(Entity.class);
            if (entity != null && entity.name().equals(type) || aClass.getName().equals(type) || aClass.getSimpleName().equals(type)) {
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
        return Optional.empty();
    }

    @SneakyThrows
    @Cacheable(ENTITY_IDS_BY_CLASS_NAME)
    public Set<String> getEntityIDsByEntityClassFullName(Class<BaseEntity> entityClass) {
        List<BaseEntity> list;
        Predicate<BaseEntity> filter = baseEntity -> true;
        AbstractRepository<BaseEntity> repositoryByClass = classFinder.getRepositoryByClass(entityClass);

        // in case we not found repository, but found potential repository - we should filter
        if (!repositoryByClass.getEntityClass().equals(entityClass)) {
            filter = baseEntity -> entityClass.isAssignableFrom(baseEntity.getClass());
        }

        list = repositoryByClass.listAll();
        return list.stream().filter(filter).map(BaseEntity::getEntityID).collect(Collectors.toSet());
    }
}
