package org.homio.app.auth;

import static org.homio.bundle.api.util.Constants.ADMIN_ROLE;

import java.security.Principal;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.UserService;
import org.homio.app.model.entity.UserEntityImpl;
import org.homio.app.repository.UserRepository;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.UserEntity;
import org.homio.bundle.api.entity.UserEntity.UserType;
import org.homio.bundle.api.exception.ServerException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UserRepository userRepository;
    private final UserService userService;
    private final EntityContext entityContext;
    private final PasswordEncoder passwordEncoder;

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
        log.info("Login <{}>", credentials.getEmail());
        try {
            String username = credentials.getEmail();
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, credentials.getPassword()));
            return jwtTokenProvider.createToken(username, authentication.getAuthorities());
        } catch (AuthenticationException e) {
            throw e;
        }
    }

    @Secured(ADMIN_ROLE)
    @PostMapping("/user")
    public UserEntityImpl createGuestUser(@Valid @RequestBody UserRequest userRequest) {
        log.info("Create user <{}>. {}", userRequest.getEmail(), userRequest.getUserType());
        UserEntityImpl user = userRepository.getUser(userRequest.getEmail());
        if (user != null) {
            throw new ServerException("User already exists");
        }
        return userService.createUser(userRequest.getEmail(), userRequest.getEmail(),
            userRequest.getName(), userRequest.getPassword(), userRequest.getUserType());
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
