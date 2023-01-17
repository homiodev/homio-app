package org.touchhome.app.repository;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Log4j2
@Repository
public class UserRepository extends AbstractRepository<UserEntity> {

    public UserRepository() {
        super(UserEntity.class);
    }

    @Transactional(readOnly = true)
    public UserEntity getUser(String email) {
        return findSingleByField("userId", email);
    }
}
