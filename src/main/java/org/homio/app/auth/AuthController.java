package org.homio.app.auth;

import jakarta.ws.rs.BadRequestException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.entity.UserEntity;
import org.homio.api.entity.UserEntity.UserType;
import org.homio.api.model.Icon;
import org.homio.api.util.HardwareUtils;
import org.homio.app.manager.common.impl.ContextAddonImpl;
import org.homio.app.model.entity.user.UserAdminEntity;
import org.homio.app.model.entity.user.UserBaseEntity;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.setting.system.SystemLogoutButtonSetting;
import org.json.JSONObject;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@RestController
@RequestMapping("/rest/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final Context context;

    @GetMapping("/status")
    public StatusResponse getStatus(UsernamePasswordAuthenticationToken user) {
        if (user == null) {
            UserAdminEntity userAdminEntity = context.db().getEntityRequire(UserAdminEntity.class, PRIMARY_DEVICE);
            if (StringUtils.isBlank(userAdminEntity.getEmail())) {
                return new StatusResponse(402, HardwareUtils.APP_ID);
            }
            return new StatusResponse(401, HardwareUtils.APP_ID);
        }
        String email = UserEntityDetailsService.getEmail(user);
        String userEntityID = UserEntityDetailsService.getEntityID(user);

        addUserNotificationBlock(userEntityID, email, false);
        String version = format("%s-%s-%s-%s",
                HardwareUtils.APP_ID,
                context.setting().getApplicationVersion(),
                ContextAddonImpl.ADDON_UPDATE_COUNT,
                HardwareUtils.RUN_COUNT);
        return new StatusResponse(200, version);
    }

    @PostMapping("/register")
    public void register(@RequestBody LoginRequest credentials) {
        credentials.validate();
        UserBaseEntity.log.info("Registering <{}>", credentials.getEmail());
        try {
            UserAdminEntity userAdminEntity = context.db().getEntityRequire(UserAdminEntity.class, PRIMARY_DEVICE);
            if (StringUtils.isNotBlank(userAdminEntity.getEmail())) {
                throw new IllegalStateException("Unable to register second primary user");
            }
            userAdminEntity.setEmail(credentials.email);
            userAdminEntity.setPassword(credentials.password, context.getBean(PasswordEncoder.class));

            context.db().save(userAdminEntity);
        } catch (Exception ex) {
            UserBaseEntity.log.info("Register failed for <{}>", credentials.getEmail(), ex);
            throw ex;
        }
    }

    @GetMapping("/user")
    public UserEntity getUser() {
        return context.getUser();
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest credentials) {
        credentials.validate();
        UserBaseEntity.log.info("Login <{}>", credentials.getEmail());
        try {
            String username = credentials.getEmail();
            Authentication authentication = getAuthentication(username, credentials.password);
            UserBaseEntity.log.info("Login success for <{}>", credentials.getEmail());
            String entityID = UserEntityDetailsService.getEntityID(authentication);
            String email = UserEntityDetailsService.getEmail(authentication);
            addUserNotificationBlock(entityID, email, true);
            return jwtTokenProvider.createToken(username, authentication, TimeUnit.MINUTES.toMillis(jwtTokenProvider.getJwtValidityTimeout()));
        } catch (Exception ex) {
            UserBaseEntity.log.info("Login failed for <{}>", credentials.getEmail(), ex);
            throw ex;
        }
    }

    private Authentication getAuthentication(String username, String password) {
        if (username.equals("guest@mail.com")) {
            UserGuestEntity user = context.db().findAll(UserGuestEntity.class)
                    .stream().filter(u -> u.getPassword().asString().equals(password)).findAny().orElse(null);
            if (user != null) {
                UserDetails principal = User
                        .withUsername(user.getEntityID() + LIST_DELIMITER + user.getName())
                        .password(user.getPassword().asString())
                        .authorities(user.getRoles().toArray(new String[0]))
                        .build();
                return UsernamePasswordAuthenticationToken.authenticated(principal,
                        user.getPassword().asString(), List.of(new SimpleGrantedAuthority("GUEST")));
                // return jwtTokenProvider.createToken(username, authentication, TimeUnit.MINUTES.toMillis(jwtTokenProvider.getJwtValidityTimeout()));
                    /*User
                            .withUsername(user.getEntityID() + LIST_DELIMITER + name)
                            .password(user.getPassword().asString())
                            .authorities(roles.toArray(new String[0]))
                            .build();*/
            }
        }
        val userToken = new UsernamePasswordAuthenticationToken(username, password);
        return authenticationManager.authenticate(userToken);
    }

    private void addUserNotificationBlock(String entityID, String email, boolean replace) {
        String key = "user-" + entityID;
        if (replace || !context.ui().notification().isHasBlock(key)) {
            context.ui().notification().addBlock(key, email, new Icon("fas fa-user", "#AAAC2C"), builder ->
                    builder.visibleForUser(email)
                            .linkToEntity(context.db().getEntityRequire(entityID))
                            .setBorderColor("#AAAC2C")
                            .addInfo(key, null, "")
                            .setRightButton(new Icon("fas fa-right-from-bracket"), "W.INFO.LOGOUT", "W.CONFIRM.LOGOUT", (ignore, params) -> {
                                context.setting().setValue(SystemLogoutButtonSetting.class, new JSONObject());
                                return null;
                            }));
        }
    }

    @Getter
    @Setter
    public static class LoginRequest {

        private String email;
        private String password;

        public void validate() {
            if (email == null || email.length() < 4) {
                throw new BadRequestException("Provided email length  < 4");
            }
            if (password == null || password.length() < 4) {
                throw new BadRequestException("Provided password length  < 4");
            }
        }
    }

    @Getter
    @Setter
    public static class UserRequest extends LoginRequest {

        private String name;
        private UserType userType;
    }

    public record StatusResponse(int status, String version) {

    }
}
