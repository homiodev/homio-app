package org.touchhome.app.manager.common;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.BackgroundProcessManager;
import org.touchhome.app.model.entity.HasBackgroundProcesses;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.thread.js.AbstractJSBackgroundProcessService;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.util.ClassFinder;
import org.touchhome.bundle.api.util.SmartUtils;

import javax.persistence.Entity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.touchhome.app.manager.CacheService.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class EntityManager {

    private static Map<String, Class<? extends BaseEntity>> baseEntityClasses = new Reflections("org.touchhome").getSubTypesOf(BaseEntity.class).stream().collect(Collectors.toMap(Class::getName, s -> s));

    private final ClassFinder classFinder;
    private final BackgroundProcessManager backgroundProcessManager;

    @Autowired
    private InternalManager entityContext;

    <T extends BaseEntity> T getEntityNoCache(String entityID) {
        return getEntity(entityID);
    }

    @Cacheable(ENTITY_WITH_FETCH_LAZY_IGNORE_NOT_UI)
    public <T extends BaseEntity> T getEntityWithFetchLazy(String entityID) {
        return (T) getRepositoryByEntityID(entityID)
                .map(r -> r.getByEntityIDWithFetchLazy(entityID, false))
                .orElse(null);
    }

    @Cacheable(value = ENTITY_BY_ENTITY_ID)
    public <T extends BaseEntity> T getEntity(String entityID) {
        return (T) getRepositoryByEntityID(entityID)
                .map(r -> r.getByEntityID(entityID))
                .orElse(null);
    }

    @Cacheable(value = ENTITY_BY_ENTITY_ID, key = "#entityID")
    public <T> T getEntity(String entityID, Class<T> targetClass) {
        AbstractRepository<BaseEntity> repository = getRepositoryByEntityID(entityID).
                orElseThrow(() -> new IllegalArgumentException("Can't find repository for entity: " + entityID));

        BaseEntity<? extends BaseEntity> baseEntity = repository.getByEntityID(entityID);
        if (!targetClass.isAssignableFrom(baseEntity.getClass())) {
            throw new NotFoundException("Repository: " + repository.getEntityClass().getSimpleName() + " has no " + targetClass.getSimpleName() + " implementation");
        }
        return (T) baseEntity;
    }

    @Cacheable(CACHE_CLASS_BY_TYPE)
    public Class<? extends BaseEntity> getClassByType(String type) {
        for (Class<? extends BaseEntity> aClass : baseEntityClasses.values()) {
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

    <T extends BaseEntity> void updateBGPProcesses(T entity) {
        if (entity instanceof HasBackgroundProcesses) {
            for (ScriptEntity scriptEntity : ((HasBackgroundProcesses) entity).getAvailableProcesses()) {
                updateBGPProcess(scriptEntity);
            }
        }
        if (entity instanceof ScriptEntity) {
            updateBGPProcess((ScriptEntity) entity);
        }
    }

    private void updateBGPProcess(ScriptEntity scriptEntity) {
        try {
            AbstractJSBackgroundProcessService service = null;
            try {
                service = scriptEntity.createBackgroundProcessService();
            } catch (Exception ex) { // when we delete item we may got this exception
                log.error(ex.getMessage());
            }
            try {
                if (service != null) {
                    backgroundProcessManager.fireIfNeedRestart(service);
                }
            } catch (Exception ex) {
                service.setStatus(BackgroundProcessStatus.FAILED, SmartUtils.getErrorMessage(ex));
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Cacheable(REPOSITORY_BY_ENTITY_ID)
    public Optional<AbstractRepository> getRepositoryByEntityID(String entityID) {
        if (entityID != null) {
            for (AbstractRepository abstractRepository : InternalManager.repositories.values()) {
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
            filter = baseEntity -> baseEntity.getClass().isAssignableFrom(entityClass);
        }

        list = repositoryByClass.listAll();
        return list.stream().filter(filter).map(BaseEntity::getEntityID).collect(Collectors.toSet());
    }
}
