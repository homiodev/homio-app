package org.homio.app.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.Hibernate;
import org.homio.api.entity.BaseEntity;
import org.homio.api.ui.field.UIField;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.TransactionManagerContext;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public class AbstractRepository<T extends BaseEntity> {

    private final Class<T> clazz;
    private final String prefix;

    @Autowired
    protected TransactionManagerContext tmc;

    public AbstractRepository(Class<T> clazz) {
        this.clazz = clazz;
        this.prefix = Modifier.isAbstract(clazz.getModifiers()) ? null : CommonUtils.newInstance(clazz).getEntityPrefix();
    }

    public T getByName(String name) {
        return findSingleByField("name", name);
    }

    /**
     * Warning!!!
     * Must be called only from EntityManager. That's why it's not transactional
     * @param entity - object to save
     * @return saved entity
     */
    public T save(T entity) {
        return tmc.executeWithoutTransaction(em -> em.merge(entity));
    }

    public void flushCashedEntity(T entity) {
        tmc.executeInTransaction(em -> {
            em.merge(entity);
        });
    }

    public List<T> listAll() {
        String sql = "FROM " + getEntityClass().getSimpleName();
        return tmc.executeInTransactionReadOnly(em ->
            em.createQuery(sql, getEntityClass()).getResultList());
    }

    public T getByEntityID(String entityID) {
        return findSingleByField("entityID", entityID);
    }

    protected List<T> findByField(EntityManager entityManager, String fieldName, Object value) {
        String sql = "FROM " + getEntityClass().getSimpleName() + " where " + fieldName + " = :value";
        return entityManager.createQuery(sql, getEntityClass()).setParameter("value", value).getResultList();
    }

    public Long size() {
        String sql = "SELECT count(t.id) FROM " + getEntityClass().getSimpleName() + " as t";
        return tmc.executeWithoutTransaction(em -> em.createQuery(sql, Long.class).getSingleResult());
    }

    protected T findSingleByField(String fieldName, Object value) {
        return tmc.executeInTransactionReadOnly(entityManager ->
            findByField(entityManager, fieldName, value).stream().findFirst().orElse(null));
    }

    public T getByEntityIDWithFetchLazy(String entityID, boolean ignoreNotUILazy) {
        T byEntityID = getByEntityID(entityID);
        if (byEntityID != null) {
            fetchLazy(byEntityID, new HashSet<>(), ignoreNotUILazy);
        }
        return byEntityID;
    }

    private void fetchLazy(Object entity, Set<Object> visitedEntities, boolean ignoreNotUI) {
        FieldUtils.getAllFieldsList(entity.getClass()).forEach(field -> {
            try {
                if (ignoreNotUI && field.getAnnotation(UIField.class) == null) {
                    return;
                }
                if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(OneToOne.class) ||
                        field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(ManyToMany.class)) {
                    Object proxy = FieldUtils.readField(field, entity, true);
                    Hibernate.initialize(proxy);
                    if (proxy != null && visitedEntities.add(proxy)) {
                        if (proxy instanceof Collection) {
                            ((Collection<?>) proxy).forEach(o -> fetchLazy(o, visitedEntities, ignoreNotUI));
                        } else {
                            fetchLazy(proxy, visitedEntities, ignoreNotUI);
                        }
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public T deleteByEntityID(String entityID) {
        return tmc.executeInTransaction(em -> {
            T entity = findByField(em, "entityID", entityID).stream().findFirst().orElse(null);
            if (entity != null) {
                em.remove(entity);
                log.warn("Entity <{}> was removed", entity);
            }
            return entity;
        });
    }

    public Class<T> getEntityClass() {
        return clazz;
    }

    public boolean isMatch(String entityID) {
        return prefix != null && entityID.startsWith(prefix);
    }
}
