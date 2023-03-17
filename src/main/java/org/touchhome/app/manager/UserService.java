package org.touchhome.app.manager;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;
import static org.touchhome.bundle.api.util.Constants.GUEST_ROLE;
import static org.touchhome.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.entity.UserEntity.UserType;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final EntityContext entityContext;

    @SneakyThrows
    public void ensureUserExists() {
        List<UserEntity> users = entityContext.findAll(UserEntity.class);
        if (users.isEmpty()) {
            log.info("Try create primary user");
            UserEntity userEntity = new UserEntity()
                .setEntityID("primary")
                .setPassword("adminadmin", passwordEncoder)
                .setUserId("admin@gmail.com")
                .setUserType(UserType.REGULAR)
                .setName("admin")
                .setRoles(new HashSet<>(Arrays.asList(ADMIN_ROLE, PRIVILEGED_USER_ROLE, GUEST_ROLE)));
            entityContext.save(userEntity);
            log.info("Primary user created successfully");
        }
    }
}
