package org.touchhome.app.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;

import java.security.Principal;

import static org.touchhome.bundle.api.model.UserEntity.ADMIN_USER;

@Log4j2
@RestController
@RequestMapping("/rest/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final EntityContext entityContext;

    @GetMapping("status")
    public int getStatus(Principal user) {
        UserEntity userEntity = entityContext.getEntity(ADMIN_USER);
        return user == null ? (userEntity.isPasswordNotSet() ? 402 : 401) : 200;
    }

    @GetMapping("user")
    public UserEntity getUser() {
        return entityContext.getEntity(ADMIN_USER);
    }

    @PostMapping("login")
    public String login(@RequestBody Credentials credentials) {
        log.info("Login <{}>", credentials.getEmail());
        try {
            String username = credentials.getEmail();
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, credentials));
            return jwtTokenProvider.createToken(username, userRepository.getUser(username).getRoles());
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid username/password supplied");
        }
    }
}
