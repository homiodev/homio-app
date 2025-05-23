package org.homio.app.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.internal.SessionFactoryImpl;
import org.homio.api.util.CommonUtils;
import org.homio.app.extloader.CustomPersistenceManagedTypes;
import org.homio.app.manager.CacheService;
import org.homio.app.manager.common.ContextImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.repeat;

@Log4j2
@Component
public class TransactionManagerContext {

  private final @NotNull ApplicationContext context;
  private final @NotNull TransactionTemplate transactionTemplate;
  private final @NotNull TransactionTemplate readOnlyTransactionTemplate;
  private final @NotNull CacheService cacheService;
  private final @NotNull EntityManagerFactory entityManagerFactory;
  private final @NotNull EntityManager entityManager;
  private final @NotNull RetryTemplate dbRetryTemplate;
  private final AtomicInteger currentActiveQueries = new AtomicInteger(0);
  private EntityManagerFactory updatedEntityManagerFactory;
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

    this.dbRetryTemplate = buildRetryTemplate();
  }

  public <T> T executeInTransactionReadOnly(Function<EntityManager, T> handler) {
    return executeQuery(() -> readOnlyTransactionTemplate.execute(status -> handler.apply(entityManager)));
  }

  public <T> T executeWithoutTransaction(Function<EntityManager, T> handler) {
    return executeQuery(() -> handler.apply(entityManager));
  }

  public <T> T executeInTransaction(Function<EntityManager, T> handler) {
    return executeQuery(() ->
      dbRetryTemplate.execute(arg0 ->
        transactionTemplate.execute(status -> handler.apply(entityManager))));
  }

  public void executeInTransaction(Consumer<EntityManager> handler) {
    executeQuery(() ->
      dbRetryTemplate.execute(arg0 -> {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
          @Override
          protected void doInTransactionWithoutResult(@NotNull TransactionStatus status) {
            handler.accept(entityManager);
          }
        });
        return null;
      }));
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

    // re-register all load events
    var contextImpl = context.getBean(ContextImpl.class);
    contextImpl.event().registerEntityListeners(entityManagerFactory);
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

  private RetryTemplate buildRetryTemplate() {
    RetryTemplate retryTemplate = new RetryTemplate();

    FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
    fixedBackOffPolicy.setBackOffPeriod(500L);
    retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

    Map<Class<? extends Throwable>, RetryPolicy> exceptionMap = new HashMap<>();
    exceptionMap.put(SQLException.class, new SimpleRetryPolicy(10));
    exceptionMap.put(CannotAcquireLockException.class, new SimpleRetryPolicy(10));

    ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();
    retryPolicy.setPolicyMap(exceptionMap);

    retryTemplate.setRetryPolicy(retryPolicy);
    retryTemplate.registerListener(new RetryListener() {
      @Override
      public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        log.warn("DB unable attempt to execute command: {}", CommonUtils.getErrorMessage(throwable));
      }

      @Override
      public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        if (throwable != null) {
          log.error("DB failed to execute db command: {}", CommonUtils.getErrorMessage(throwable));
        }
      }
    });

    return retryTemplate;
  }
}
