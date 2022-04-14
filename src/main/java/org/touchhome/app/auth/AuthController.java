package org.touchhome.app.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.repository.UserRepository;
import org.touchhome.common.exception.ServerException;

import javax.validation.Valid;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;

import static org.touchhome.bundle.api.entity.UserEntity.ADMIN_USER;
import static org.touchhome.bundle.api.util.Constants.*;

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
        UserEntity userEntity = entityContext.getEntity(ADMIN_USER);
        return user == null ? (userEntity == null || userEntity.isPasswordNotSet(passwordEncoder) ? 402 : 401) : 200;
    }

    @GetMapping("/user")
    public UserEntity getUser() {
        return entityContext.getEntity(ADMIN_USER);
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody Credentials credentials) {
        log.info("Login <{}>", credentials.getEmail());
        try {
            String username = credentials.getEmail();
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, credentials));
            return jwtTokenProvider.createToken(username, userRepository.getUser(username).getRoles());
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid username/password supplied");
        }
    }

    @Secured(ADMIN_ROLE)
    @PostMapping("/user/guest")
    public UserEntity createGuestUser(@Valid @RequestBody Credentials credentials) {
        return createUser(credentials, GUEST_ROLE);
    }

    @Secured(ADMIN_ROLE)
    @PostMapping("/user/privileged")
    public UserEntity createPrivilegedUser(@Valid @RequestBody Credentials credentials) {
        return createUser(credentials, PRIVILEGED_USER_ROLE, GUEST_ROLE);
    }

    private UserEntity createUser(@Valid @RequestBody Credentials credentials, String... roles) {
        log.info("Create guest user <{}>", credentials.getEmail());
        UserEntity user = userRepository.getUser(credentials.getEmail());
        if (user != null) {
            throw new ServerException("User already exists");
        }
        return entityContext.save(new UserEntity().computeEntityID(credentials::getEmail)
                .setUserId(credentials.getEmail())
                .setPassword(credentials.getPassword(), this.passwordEncoder)
                .setRoles(new HashSet<>(Arrays.asList(roles))));
    }
}
