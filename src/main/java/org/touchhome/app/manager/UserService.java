package org.touchhome.app.manager;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;
import static org.touchhome.bundle.api.util.Constants.GUEST_ROLE;
import static org.touchhome.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;
import static org.touchhome.common.util.CommonUtils.OBJECT_MAPPER;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.common.util.CommonUtils;

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
            log.info("Try create admin user");
            Path userPasswordFilePath = CommonUtils.getRootPath().resolve("user_password.conf");
            if (!Files.exists(userPasswordFilePath)) {
                throw new RuntimeException("Unable to start app without file user_password.conf");
            }
            UserPasswordFile userPasswordFile = OBJECT_MAPPER.readValue(userPasswordFilePath.toFile(), UserPasswordFile.class);
            UserEntity userEntity = new UserEntity()
                .setEntityID(userPasswordFile.email)
                .setPassword(userPasswordFile.password, passwordEncoder)
                .setUserId(userPasswordFile.email)
                .setRoles(new HashSet<>(Arrays.asList(ADMIN_ROLE, PRIVILEGED_USER_ROLE, GUEST_ROLE)));

            Path initPrivateKey = CommonUtils.getRootPath().resolve("init_private_key");
            if (Files.exists(initPrivateKey)) {
                userEntity.setKeystore(Files.readAllBytes(initPrivateKey));
            }
            entityContext.save(userEntity);
            Files.delete(userPasswordFilePath);
            Files.deleteIfExists(initPrivateKey);
            log.info("Admin user created successfully");
        }
    }

    @Getter
    @Setter
    private static class UserPasswordFile {

        private String email;
        private String password;
    }
}
