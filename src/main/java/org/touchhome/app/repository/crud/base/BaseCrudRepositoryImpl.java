package org.touchhome.app.repository.crud.base;

import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.touchhome.bundle.api.model.HasEntityIdentifier;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@NoRepositoryBean
public class BaseCrudRepositoryImpl<T extends HasEntityIdentifier> extends SimpleJpaRepository<T, Integer> implements BaseCrudRepository<T> {

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
