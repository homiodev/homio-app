package org.homio.app.config;

import static org.apache.commons.lang3.StringUtils.repeat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.internal.SessionFactoryImpl;
import org.homio.app.extloader.CustomPersistenceManagedTypes;
import org.homio.app.manager.CacheService;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@Log4j2
@Component
public class TransactionManagerContext {

    private final @NotNull ApplicationContext context;
    private final @NotNull TransactionTemplate transactionTemplate;
    private final @NotNull TransactionTemplate readOnlyTransactionTemplate;
    private final @NotNull CacheService cacheService;
    private final @NotNull EntityManagerFactory entityManagerFactory;
    private final @NotNull EntityManager entityManager;

    private EntityManagerFactory updatedEntityManagerFactory;
    private final AtomicInteger currentActiveQueries = new AtomicInteger(0);
    private volatile CountDownLatch blocker;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public TransactionManagerContext(
        ApplicationContext context,
        CacheService cacheService,
        PlatformTransactionManager transactionManager,
        EntityManagerFactory entityManagerFactory,
        EntityManager entityManager) {
        this.context = context;
        this.cacheService = cacheService;

        this.entityManagerFactory = entityManagerFactory;
        this.entityManager = entityManager;

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    public <T> T executeInTransactionReadOnly(Function<EntityManager, T> handler) {
        return executeQuery(() -> readOnlyTransactionTemplate.execute(status -> handler.apply(entityManager)));
    }

    public <T> T executeWithoutTransaction(Function<EntityManager, T> handler) {
        return executeQuery(() -> handler.apply(entityManager));
    }

    public <T> T executeInTransaction(Function<EntityManager, T> handler) {
        return executeQuery(() -> transactionTemplate.execute(status -> handler.apply(entityManager)));
    }

    public void executeInTransaction(Consumer<EntityManager> handler) {
        executeQuery(() -> {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(@NotNull TransactionStatus status) {
                    handler.accept(entityManager);
                }
            });
            return null;
        });
    }

    @SneakyThrows
    public synchronized void invalidate() {
        if (!context.getBean(CustomPersistenceManagedTypes.class).scan()) {
            return;
        }

        String delimiter = repeat("-", 50);
        log.info("\n#{}\nUpdating EntityManagerFactory...\n#{}", delimiter, delimiter);
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);

        EntityManagerFactory newEntityManagerFactory = createEntityManagerFactory();
        SessionFactoryImpl newSessionFactory = newEntityManagerFactory.unwrap(SessionFactoryImpl.class);

        blocker = new CountDownLatch(1);
        while (currentActiveQueries.get() != 0) {
            log.info("Wait all db session to finish");
            Thread.sleep(100);
        }
        if (updatedEntityManagerFactory != null) {
            updatedEntityManagerFactory.close();
        }
        this.updatedEntityManagerFactory = newEntityManagerFactory;

        try {
            FieldUtils.writeDeclaredField(sessionFactory, "eventEngine", newSessionFactory.getEventEngine(), true);
            FieldUtils.writeDeclaredField(sessionFactory, "queryEngine", newSessionFactory.getQueryEngine(), true);
            FieldUtils.writeDeclaredField(sessionFactory, "cacheAccess", newSessionFactory.getCache(), true);
            FieldUtils.writeDeclaredField(sessionFactory, "runtimeMetamodels", newSessionFactory.getRuntimeMetamodels(), true);
            FieldUtils.writeDeclaredField(sessionFactory, "fastSessionServices", newSessionFactory.getFastSessionServices(), true);
            FieldUtils.writeDeclaredField(sessionFactory, "wrapperOptions", newSessionFactory.getWrapperOptions(), true);

            this.cacheService.clearCache();
            log.info("\n#{}\nEntityManagerFactory updated\n#{}", delimiter, delimiter);
        } catch (Exception ex) {
            log.error("\n#{}\nEntityManagerFactory failed to update\n#{}", delimiter, delimiter, ex);
            throw new RuntimeException(ex);
        } finally {
            blocker.countDown();
            blocker = null;
        }
    }

    private EntityManagerFactory createEntityManagerFactory() {
        Map<String, Object> properties = context
            .getBean(HibernateProperties.class)
            .determineHibernateProperties(
                context.getBean(JpaProperties.class)
                       .getProperties(),
                new HibernateSettings().ddlAuto(() -> "none"));
        Map<String, Object> vendorProperties = new LinkedHashMap<>(properties);
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
            context.getBean(EntityManagerFactoryBuilder.class)
                   .dataSource(context.getBean(DataSource.class))
                   .managedTypes(context.getBean(CustomPersistenceManagedTypes.class))
                   .properties(vendorProperties)
                   .build();
        entityManagerFactoryBean.afterPropertiesSet();
        return Objects.requireNonNull(entityManagerFactoryBean.getObject());
    }

    @SneakyThrows
    private <T> T executeQuery(Supplier<T> handler) {
        try {
            if (blocker != null) {
                blocker.await();
            }
            currentActiveQueries.incrementAndGet();
            return handler.get();
        } finally {
            currentActiveQueries.decrementAndGet();
        }
    }
}
