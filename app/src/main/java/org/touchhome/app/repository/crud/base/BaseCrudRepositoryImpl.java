package org.touchhome.app.repository.crud.base;

import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.touchhome.bundle.api.model.PureEntity;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@NoRepositoryBean
public class BaseCrudRepositoryImpl<T extends PureEntity> extends SimpleJpaRepository<T, Integer> implements BaseCrudRepository<T> {

    private EntityManager entityManager;

    BaseCrudRepositoryImpl(Class<T> domainClass, EntityManager entityManager) {
        super(domainClass, entityManager);
        this.entityManager = entityManager;
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
