package org.homio.app.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.homio.api.repository.EntityManagerContext;
import org.homio.app.extloader.CustomPersistenceManagedTypes;
import org.homio.app.manager.CacheService;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.stereotype.Component;

@Component
public class EntityManagerContextImpl implements EntityManagerContext {

    private final ApplicationContext applicationContext;
    private final Map<String, Object> jpaProperties;
    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    public EntityManagerContextImpl(ApplicationContext applicationContext, EntityManagerFactory entityManagerFactory) {
        this.applicationContext = applicationContext;
        this.entityManagerFactory = entityManagerFactory;
        this.jpaProperties = applicationContext.getBean(LocalContainerEntityManagerFactoryBean.class).getJpaPropertyMap();
    }

    @Override
    public EntityManager getEntityManager() {
        if (entityManager == null) {
            entityManager = entityManagerFactory.createEntityManager();
        }
        return entityManager;
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
        entityManagerFactory = factoryBean.getObject();

        EntityManager oldEntityManager = entityManager;
        entityManager = null;
        oldEntityManager.close();
        applicationContext.getBean(CacheService.class).clearCache();
    }
}
