package org.touchhome.bundle.api.repository.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class UserRepository extends AbstractRepository<UserEntity> {

    public static final String PREFIX = "u_";

    public static final String DEFAULT_USER_ID = PREFIX + "user";
    private final EntityContext entityContext;

    public UserRepository(EntityContext entityContext) {
        super(UserEntity.class, PREFIX);
        this.entityContext = entityContext;
    }

    public void postConstruct() {
        UserEntity entity = entityContext.getEntity(DEFAULT_USER_ID);
        if (entity == null) {
            entity = new UserEntity();
            entityContext.save(entity.computeEntityID(() -> DEFAULT_USER_ID));
        }
    }
}

















