package org.homio.app.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.app.extloader.CustomPersistenceManagedTypes;
import org.homio.app.manager.CacheService;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@Log4j2
//@Component
public class TransactionManagerImpl {

    private final ApplicationContext applicationContext;
    private final Map<String, Object> jpaProperties;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final JpaTransactionManager jpaTransactionManager;
    private EntityManagerFactory entityManagerFactory;
    @Getter
    private EntityManager entityManager;

    public TransactionManagerImpl(
        ApplicationContext applicationContext,
        EntityManagerFactory entityManagerFactory) {
        this.applicationContext = applicationContext;
        this.entityManagerFactory = entityManagerFactory;

        this.jpaProperties = applicationContext.getBean(LocalContainerEntityManagerFactoryBean.class).getJpaPropertyMap();
        this.jpaTransactionManager = (JpaTransactionManager) applicationContext.getBean("transactionManager");

        this.transactionTemplate = new TransactionTemplate(jpaTransactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(jpaTransactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);

        this.entityManager = entityManagerFactory.createEntityManager();
    }

    /*@Override
    public <T> T executeInTransactionReadOnly(Function<EntityManager, T> handler) {
        return readOnlyTransactionTemplate.execute(status -> {
            return handler.apply(entityManager);
        });
    }*/

    public <T> T executeInTransaction(Function<EntityManager, T> handler) {
        return transactionTemplate.execute(status -> handler.apply(entityManager));
    }

    public void executeInTransaction(Consumer<EntityManager> handler) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                handler.accept(entityManager);
            }
        });
    }

    public synchronized void invalidate() {
        if (!applicationContext.getBean(CustomPersistenceManagedTypes.class).scan()) {
            return;
        }
        LocalContainerEntityManagerFactoryBean factoryBean = applicationContext
            .getBean(EntityManagerFactoryBuilder.class)
            .dataSource(applicationContext.getBean(DataSource.class))
            .managedTypes(applicationContext.getBean(PersistenceManagedTypes.class))
            .properties(jpaProperties)
            .build();
        factoryBean.afterPropertiesSet();

        EntityManagerFactory oldEntityManagerFactory = entityManagerFactory;
        EntityManager oldEntityManager = entityManager;

        entityManagerFactory = Objects.requireNonNull(factoryBean.getObject());
        entityManager = entityManagerFactory.createEntityManager();
        applicationContext.getBean(CacheService.class).clearCache();
        applicationContext.getBean(EntityContext.class).bgp()
                          .builder("destroy-old-entity-manager")
                          .delay(Duration.ofSeconds(10))
                          .execute(() -> {
                              log.info("Close old entityManagerFactory");
                              oldEntityManager.close(); // maybe redundant
                              oldEntityManagerFactory.close();
                          });
    }
}
