package org.homio.app.repository;

import lombok.extern.log4j.Log4j2;
import org.homio.app.model.entity.UserEntityImpl;
import org.homio.bundle.api.entity.UserEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Repository
public class UserRepository extends AbstractRepository<UserEntityImpl> {

    public UserRepository() {
        super(UserEntityImpl.class);
    }

    @Transactional(readOnly = true)
    public @Nullable UserEntityImpl getUser(String name) {
        return em.createQuery("FROM " + getEntityClass().getSimpleName() + " where email = :value OR name = :value", getEntityClass())
                 .setParameter("value", name).getResultList().stream().findAny().orElse(null);
    }
}
