package org.homio.app.repository.crud.base;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import org.homio.bundle.api.model.HasEntityIdentifier;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public class BaseCrudRepositoryImpl<T extends HasEntityIdentifier>
        extends SimpleJpaRepository<T, Integer> implements BaseCrudRepository<T> {

    BaseCrudRepositoryImpl(Class<T> domainClass, EntityManager entityManager) {
        super(domainClass, entityManager);
    }

    @Override
    @Transactional
    public void flushCashedEntity(T entity) {
        super.save(entity);
    }

    @Override
    public Class<T> getEntityClass() {
        return super.getDomainClass();
    }
}
