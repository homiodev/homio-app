package org.homio.app.repository.crud.base;

import jakarta.persistence.EntityManager;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.app.config.TransactionManagerContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

public class CrudRepositoryFactoryBean<R extends JpaRepository<T, Integer>, T extends HasEntityIdentifier>
    extends JpaRepositoryFactoryBean<R, T, Integer> {

    private final TransactionManagerContext tmc;

    /**
     * Creates a new {@link JpaRepositoryFactoryBean} for the given repository interface.
     *
     * @param repositoryInterface must not be {@literal null}.
     */
    public CrudRepositoryFactoryBean(Class<? extends R> repositoryInterface, TransactionManagerContext tmc) {
        super(repositoryInterface);
        this.tmc = tmc;
    }

    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        return new MyRepositoryFactory(entityManager);
    }

    private class MyRepositoryFactory<T extends HasEntityIdentifier> extends JpaRepositoryFactory {

        MyRepositoryFactory(EntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected JpaRepositoryImplementation<?, ?> getTargetRepository(RepositoryInformation information, EntityManager entityManager) {
            return new BaseCrudRepositoryImpl<>((Class<T>) information.getDomainType(), entityManager, tmc);
        }

        protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
            return BaseCrudRepository.class;
        }
    }
}
