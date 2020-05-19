package org.touchhome.bundle.api.repository.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

import static org.touchhome.bundle.api.model.UserEntity.ADMIN_USER;
import static org.touchhome.bundle.api.model.UserEntity.PREFIX;

@Repository
public class UserRepository extends AbstractRepository<UserEntity> {

    public UserRepository() {
        super(UserEntity.class, PREFIX);
    }

    public void postConstruct(EntityContext entityContext) {
        UserEntity entity = entityContext.getEntity(ADMIN_USER);
        if (entity == null) {
            entity = new UserEntity();
            entityContext.save(entity.computeEntityID(() -> ADMIN_USER));
        }
    }

    public UserEntity getUser(String email) {
        return findSingleByField("userId", email);
    }
}

















