package org.touchhome.app.repository.crud.base;

import javax.persistence.EntityManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.touchhome.bundle.api.model.HasEntityIdentifier;

public class CrudRepositoryFactoryBean<R extends JpaRepository<T, Integer>, T extends HasEntityIdentifier>
    extends JpaRepositoryFactoryBean<R, T, Integer> {

  /**
   * Creates a new {@link JpaRepositoryFactoryBean} for the given repository interface.
   *
   * @param repositoryInterface must not be {@literal null}.
   */
  public CrudRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
    super(repositoryInterface);
  }

  protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
    return new MyRepositoryFactory(entityManager);
  }

  private static class MyRepositoryFactory<T extends HasEntityIdentifier> extends JpaRepositoryFactory {

    MyRepositoryFactory(EntityManager entityManager) {
      super(entityManager);
    }

    @Override
    protected JpaRepositoryImplementation<?, ?> getTargetRepository(RepositoryInformation information,
        EntityManager entityManager) {
      return new BaseCrudRepositoryImpl<>((Class<T>) information.getDomainType(), entityManager);
    }

    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
      return BaseCrudRepository.class;
    }
  }
}
