package org.homio.app.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.Hibernate;
import org.homio.api.entity.BaseEntity;
import org.homio.api.ui.field.UIField;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.TransactionManagerContext;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.var.WorkspaceVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

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
        String sql = "FROM " + getEntityClass().getSimpleName() + " where " + fieldName + " = :value";
        return entityManager.createQuery(sql, getEntityClass()).setParameter("value", value).getResultList();
    }

    public @NotNull Long size() {
        String sql = "SELECT count(t.id) FROM " + getEntityClass().getSimpleName() + " as t";
        return tmc.executeWithoutTransaction(em -> em.createQuery(sql, Long.class).getSingleResult());
    }

    protected @Nullable T findSingleByField(@NotNull String fieldName, @NotNull Object value) {
        return tmc.executeInTransactionReadOnly(entityManager ->
                findByField(entityManager, fieldName, value).stream().findFirst().orElse(null));
    }

    public @Nullable T getByEntityIDWithFetchLazy(@NotNull String entityID, boolean ignoreNotUILazy) {
        return tmc.executeInTransactionReadOnly(em -> {
            T entity = getEntity(entityID, em);
            if (entity != null) {
                fetchLazy(entity, new HashSet<>(), ignoreNotUILazy);
            }
            return entity;
        });
    }

    private void fetchLazy(@NotNull Object entity, @NotNull Set<Object> visitedEntities, boolean ignoreNotUI) {
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

    public @Nullable T deleteByEntityID(@NotNull String entityID) {
        return tmc.executeInTransaction(em -> {
            T entity = getEntity(entityID, em);
            if (entity != null) {
                // TODO: issue: https://hibernate.atlassian.net/browse/HHH-17036
                fetchLazy(entity, new HashSet<>(), false);
                em.remove(entity);
                // somehow cascade = CascadeType.ALL now works
                if (entity instanceof WidgetSeriesEntity<?> wse) {
                    wse.getWidgetEntity().getSeries().remove(entity);
                } else if (entity instanceof WorkspaceVariable wv) {
                    wv.getWorkspaceGroup().getWorkspaceVariables().remove(entity);
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
