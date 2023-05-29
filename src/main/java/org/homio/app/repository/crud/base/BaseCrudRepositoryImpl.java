package org.homio.app.repository.crud.base;

import jakarta.persistence.EntityManager;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.app.config.TransactionManagerContext;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

@NoRepositoryBean
public class BaseCrudRepositoryImpl<T extends HasEntityIdentifier>
    extends SimpleJpaRepository<T, Integer> implements BaseCrudRepository<T> {

    private final TransactionManagerContext tmc;

    BaseCrudRepositoryImpl(Class<T> domainClass, EntityManager entityManager, TransactionManagerContext tmc) {
        super(domainClass, entityManager);
        this.tmc = tmc;
    }

    @Override
    @Transactional
    public void flushCashedEntity(T entity) {
        tmc.executeInTransaction(entityManager -> {
            super.save(entity);
        });
    }

    @Override
    public Class<T> getEntityClass() {
        return super.getDomainClass();
    }
}
