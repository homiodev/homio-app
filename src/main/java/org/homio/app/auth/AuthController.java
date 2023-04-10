package org.homio.app.auth;

import static org.homio.bundle.api.util.Constants.ADMIN_ROLE;
import static org.homio.bundle.api.util.Constants.GUEST_ROLE;
import static org.homio.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.app.repository.UserRepository;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.UserEntity;
import org.homio.bundle.api.exception.ServerException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final EntityContext entityContext;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/status")
    public int getStatus(Principal user) {
        return user == null ? 401 : 200;
    }

    @GetMapping("/user")
    public UserEntity getUser() {
        return entityContext.getUser(false);
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest credentials) {
        log.info("Login <{}>", credentials.getEmail());
        try {
            String username = credentials.getEmail();
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, credentials.getPassword()));
            return jwtTokenProvider.createToken(username, authentication.getAuthorities());
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid username/password supplied");
        }
    }

    @Secured(ADMIN_ROLE)
    @PostMapping("/user/guest")
    public UserEntity createGuestUser(@Valid @RequestBody LoginRequest credentials) {
        return createUser(credentials, GUEST_ROLE);
    }

    @Secured(ADMIN_ROLE)
    @PostMapping("/user/privileged")
    public UserEntity createPrivilegedUser(@Valid @RequestBody LoginRequest credentials) {
        return createUser(credentials, PRIVILEGED_USER_ROLE, GUEST_ROLE);
    }

    private UserEntity createUser(@Valid @RequestBody LoginRequest credentials, String... roles) {
        log.info("Create guest user <{}>", credentials.getEmail());
        UserEntity user = userRepository.getUser(credentials.getEmail());
        if (user != null) {
            throw new ServerException("User already exists");
        }
        return entityContext.save(new UserEntity()
            .setEntityID(credentials.getEmail())
            .setUserId(credentials.getEmail())
            .setPassword(credentials.getPassword(), this.passwordEncoder)
            .setRoles(new HashSet<>(Arrays.asList(roles))));
    }

    @Getter
    @Setter
    public static class LoginRequest {

        private String email;
        private String password;
    }
}