package org.homio.app.manager;

import static org.homio.bundle.api.util.Constants.ADMIN_ROLE;
import static org.homio.bundle.api.util.Constants.GUEST_ROLE;
import static org.homio.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.UserEntityImpl;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.UserEntity.UserType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final EntityContext entityContext;

    public UserEntityImpl createUser(String entityID, String email, String name, String password, UserType userType) {
        UserEntityImpl userEntity = new UserEntityImpl()
            .setEntityID(entityID)
            .setPassword(password, passwordEncoder)
            .setEmail(email)
            .setUserType(userType)
            .setName(name)
            .setRoles(new HashSet<>(buildRoles(userType)));
        entityContext.save(userEntity);
        log.info("User {} created successfully", email);
        return userEntity;
    }

    public static void ensureUserExists(EntityContextImpl entityContext) {
        List<UserEntityImpl> users = entityContext.findAll(UserEntityImpl.class);
        if (users.isEmpty()) {
            entityContext.getBean(UserService.class).createUser("primary",
                "admin@gmail.com", "admin", "adminadmin", UserType.ADMIN);
        }
    }

    /*private void userConfiguration() {
        getBean(UserService.class).ensureUserExists();

        setting().listenValueInRequest(SystemUserSetting.class, "user", json -> {
            if (json != null) {
                UserEntity user = getUserRequire();
                // authenticate
                AuthenticationProvider authenticationProvider = getBean(AuthenticationProvider.class);
                authenticationProvider.authenticate(new UsernamePasswordAuthenticationToken(
                    user.getUserId(), json.getString("field.currentPassword")));
                if (!json.getString("field.newPassword").equals(json.getString("field.repeatNewPassword"))) {
                    throw new IllegalArgumentException("user.password_not_match");
                }

                PasswordEncoder passwordEncoder = getBean(PasswordEncoder.class);
                save(user
                    .setUserId(json.getString("field.email"))
                    .setName(json.getString("field.name"))
                    .setPassword(json.getString("field.newPassword"), passwordEncoder));
                ui().sendSuccessMessage("user.altered");
                ui().reloadWindow("user.altered_reload");
            }
        });
    }*/

    private List<String> buildRoles(UserType userType) {
        switch (userType) {
            case ADMIN:
                return Arrays.asList(ADMIN_ROLE, PRIVILEGED_USER_ROLE, GUEST_ROLE);
            case PRIVILEGED:
                return Arrays.asList(PRIVILEGED_USER_ROLE, GUEST_ROLE);
            default:
                return List.of(GUEST_ROLE);
        }
    }
}
