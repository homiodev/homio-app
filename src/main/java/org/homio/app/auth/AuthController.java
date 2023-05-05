package org.homio.app.auth;

import java.security.Principal;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.homio.app.model.entity.user.UserBaseEntity;
import org.homio.app.setting.system.SystemLogoutButtonSetting;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.UserEntity;
import org.homio.bundle.api.entity.UserEntity.UserType;
import org.json.JSONObject;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EntityContext entityContext;

    @GetMapping("/status")
    public int getStatus(Principal user) {
        return user == null ? 401 : 200;
    }

    @GetMapping("/user")
    public UserEntity getUser() {
        return entityContext.getUser();
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest credentials) {
        UserBaseEntity.log.info("Login <{}>", credentials.getEmail());
        try {
            String username = credentials.getEmail();
            val user = new UsernamePasswordAuthenticationToken(username, credentials.getPassword());
            Authentication authentication = authenticationManager.authenticate(user);
            UserBaseEntity.log.info("Login success for <{}>", credentials.getEmail());
            entityContext.ui().addNotificationBlock("user", "user-" + username, "fas fa-user", "#AAAC2C", builder -> {
                builder.addButtonInfo("Logout", "#FF00FF", "fas fa-right-from-bracket", "#FFFF00",
                    "fas fa-right-from-bracket", username,
                    "W.CONFIRM.LOGOUT", (entityContext1, params) -> {
                        entityContext.setting().setValue(SystemLogoutButtonSetting.class, new JSONObject());
                        return null;
                    });
            });
            return jwtTokenProvider.createToken(username, authentication);
        } catch (Exception ex) {
            UserBaseEntity.log.info("Login failed for <{}>", credentials.getEmail(), ex);
            throw ex;
        }
    }

    @Getter
    @Setter
    public static class LoginRequest {

        private String email;
        private String password;
    }

    @Getter
    @Setter
    public static class UserRequest extends LoginRequest {

        private String name;
        private UserType userType;
    }
}
