package org.homio.app.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.internal.SessionFactoryImpl;
import org.homio.api.EntityContext;
import org.homio.app.extloader.CustomPersistenceManagedTypes;
import org.homio.app.manager.CacheService;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@Log4j2
@Component
public class TransactionManagerContext {

    private final @NotNull ApplicationContext applicationContext;
    private final @NotNull JpaProperties jpaProperties;
    private final @NotNull HibernateProperties hibernateProperties;
    private final @NotNull TransactionTemplate transactionTemplate;
    private final @NotNull TransactionTemplate readOnlyTransactionTemplate;
    private final @NotNull JpaTransactionManager transactionManager;
    private final @NotNull PersistenceManagedTypes persistenceManagedTypes;
    private final @NotNull EntityManagerFactoryBuilder entityManagerFactoryBuilder;
    private final @NotNull DataSource dataSource;
    private final @NotNull CacheService cacheService;

    @Getter
    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private Consumer<EntityManagerFactory> factoryListener;

    public TransactionManagerContext(
        ApplicationContext applicationContext,
        JpaProperties properties,
        CacheService cacheService,
        DataSource dataSource,
        EntityManagerFactoryBuilder entityManagerFactoryBuilder,
        PersistenceManagedTypes persistenceManagedTypes,
        HibernateProperties hibernateProperties) {
        this.applicationContext = applicationContext;
        this.jpaProperties = properties;
        this.dataSource = dataSource;
        this.cacheService = cacheService;
        this.hibernateProperties = hibernateProperties;
        this.persistenceManagedTypes = persistenceManagedTypes;
        this.entityManagerFactoryBuilder = entityManagerFactoryBuilder;

        log.info("Creating EntityManagerFactory");
        this.entityManagerFactory = createEntityManagerFactory("update");
        this.entityManager = entityManagerFactory.createEntityManager();
        log.info("EntityManagerFactory creation has been completed");

        this.transactionManager = new JpaTransactionManager(entityManagerFactory);

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    public <T> T executeInTransactionReadOnly(Function<EntityManager, T> handler) {
        return readOnlyTransactionTemplate.execute(status -> {
            return handler.apply(entityManager);
        });
    }

    public <T> T executeWithoutTransaction(Function<EntityManager, T> handler) {
        return handler.apply(entityManager);
    }

    public <T> T executeInTransaction(Function<EntityManager, T> handler) {
        return transactionTemplate.execute(status -> handler.apply(entityManager));
    }

    public void executeInTransaction(Consumer<EntityManager> handler) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(@NotNull TransactionStatus status) {
                handler.accept(entityManager);
            }
        });
    }

    public synchronized void invalidate() {
        if (!applicationContext.getBean(CustomPersistenceManagedTypes.class).scan()) {
            return;
        }
        delayCloseOldEntities(entityManagerFactory, entityManager);

        this.entityManagerFactory = createEntityManagerFactory("none");
        this.entityManager = entityManagerFactory.createEntityManager();
        this.transactionManager.setEntityManagerFactory(entityManagerFactory);
        this.factoryListener.accept(entityManagerFactory);
        this.cacheService.clearCache();
    }

    public void setFactoryListener(Consumer<EntityManagerFactory> factoryListener) {
        this.factoryListener = factoryListener;
        this.factoryListener.accept(entityManagerFactory);
    }

    private EntityManagerFactory createEntityManagerFactory(String ddl) {
        Map<String, Object> vendorProperties = getVendorProperties(ddl);
        vendorProperties.put(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, "true");
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
            entityManagerFactoryBuilder
                .dataSource(dataSource)
                .managedTypes(persistenceManagedTypes)
                .properties(vendorProperties)
                .build();
        entityManagerFactoryBean.afterPropertiesSet();
        EntityManagerFactory entityManagerFactory = Objects.requireNonNull(entityManagerFactoryBean.getObject());
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);

        return entityManagerFactory;
    }

    private Map<String, Object> getVendorProperties(String ddl) {
        return new LinkedHashMap<>(this.hibernateProperties.determineHibernateProperties(
            jpaProperties.getProperties(), new HibernateSettings().ddlAuto(() -> ddl)));
    }

    private void delayCloseOldEntities(EntityManagerFactory emf, EntityManager em) {
        applicationContext.getBean(EntityContext.class).bgp()
                          .builder("destroy-old-entity-manager")
                          .delay(Duration.ofSeconds(10))
                          .execute(() -> {
                              log.info("Close old EntityManagerFactory");
                              em.close(); // maybe redundant
                              emf.close();
                          });
    }
}
