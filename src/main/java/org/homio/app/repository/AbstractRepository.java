package org.homio.app.repository;

import jakarta.persistence.*;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.device.DeviceSeriesEntity;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.TransactionManagerContext;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log4j2
public class AbstractRepository<T extends BaseEntity> {

    private final @NotNull Class<T> clazz;
    @Getter
    private final @NotNull String prefix;

    @Autowired
    protected TransactionManagerContext tmc;

    public AbstractRepository(@NotNull Class<T> clazz) {
        this.clazz = clazz;
        this.prefix = CommonUtils.newInstance(clazz).getEntityPrefix();
    }

    public AbstractRepository(@NotNull Class<T> clazz, @NotNull String prefix) {
        this.clazz = clazz;
        this.prefix = prefix;
    }

    public @Nullable T getByName(@NotNull String name) {
        return findSingleByField("name", name);
    }

    /**
     * Warning!!! Must be called only from EntityManager. That's why it's not transactional
     *
     * @param entity - object to save
     * @return saved entity
     */
    public @NotNull T save(@NotNull T entity) {
        return tmc.executeWithoutTransaction(em -> em.merge(entity));
    }

    public void flushCashedEntity(@NotNull T entity) {
        tmc.executeInTransaction(em -> {
            em.merge(entity);
        });
    }

    public @NotNull List<T> listAll() {
        String sql = "FROM " + getEntityClass().getSimpleName();
        return tmc.executeInTransactionReadOnly(em ->
                em.createQuery(sql, getEntityClass()).getResultList());
    }

    public @Nullable T getByEntityID(String entityID) {
        return findSingleByField("entityID", entityID);
    }

    protected @NotNull List<T> findByField(@NotNull EntityManager entityManager, @NotNull String fieldName, @NotNull Object value) {
        var cb = entityManager.getCriteriaBuilder();
        var cq = cb.createQuery(getEntityClass());
        Root<T> root = cq.from(getEntityClass());
        cq.select(root).where(cb.equal(root.get(fieldName), value));
        return entityManager.createQuery(cq).getResultList();
    }

    public @NotNull Long size() {
        String sql = "SELECT count(t.id) FROM " + getEntityClass().getSimpleName() + " as t";
        return tmc.executeWithoutTransaction(em -> em.createQuery(sql, Long.class).getSingleResult());
    }

    protected @Nullable T findSingleByField(@NotNull String fieldName, @NotNull Object value) {
        return tmc.executeInTransactionReadOnly(entityManager ->
                findByField(entityManager, fieldName, value).stream().findFirst().orElse(null));
    }

    public @Nullable T getByEntityIDWithFetchLazy(@NotNull String entityID) {
        return tmc.executeInTransactionReadOnly(em -> {
            T entity = getEntity(entityID, em);
            if (entity != null) {
                fetchLazy(entity, new HashSet<>());
            }
            return entity;
        });
    }

    private void fetchLazy(@NotNull Object entity, @NotNull Set<Object> visitedEntities) {
        FieldUtils.getAllFieldsList(entity.getClass()).forEach(field -> {
            try {
                if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(OneToOne.class) ||
                    field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(ManyToMany.class)) {
                    Object proxy = FieldUtils.readField(field, entity, true);
                    Hibernate.initialize(proxy);
                    if (proxy instanceof HibernateProxy) {
                        proxy = Hibernate.unproxy(proxy);
                        FieldUtils.writeField(field, entity, proxy, true);
                    }
                    if (proxy != null && visitedEntities.add(proxy)) {
                        if (proxy instanceof Collection) {
                            ((Collection<?>) proxy).forEach(o -> fetchLazy(o, visitedEntities));
                        } else {
                            fetchLazy(proxy, visitedEntities);
                        }
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public @Nullable T deleteByEntityID(@NotNull String entityID) {
        return tmc.executeInTransaction(em -> {
            T entity = getEntity(entityID, em);
            if (entity != null) {
                // TODO: issue: https://hibernate.atlassian.net/browse/HHH-17036
                // TRYING TO SEE IF WORKS: fetchLazy(entity, new HashSet<>());
                em.remove(entity);
                // somehow cascade = CascadeType.ALL now works
                switch (entity) {
                    case WidgetSeriesEntity<?> wse -> wse.getWidgetEntity().getSeries().remove(entity);
                    case DeviceSeriesEntity<?> wse -> wse.getDeviceEntity().getSeries().remove(entity);
                    case WorkspaceVariable wv -> wv.getWorkspaceGroup().getWorkspaceVariables().remove(entity);
                    case WorkspaceGroup wg -> {
                        if (wg.getParent() != null) {
                            wg.getParent().getChildrenGroups().remove(wg);
                        }
                    }
                    default -> {
                    }
                }
                log.warn("Entity <{}> was removed", entity);
            }
            return entity;
        });
    }

    private @Nullable T getEntity(@NotNull String entityID, @NotNull EntityManager em) {
        return findByField(em, "entityID", entityID).stream().findFirst().orElse(null);
    }

    public @NotNull Class<T> getEntityClass() {
        return clazz;
    }

    public boolean isMatch(@NotNull String entityID) {
        return entityID.startsWith(prefix);
    }

    public boolean isUseCache() {
        return true;
    }
}
