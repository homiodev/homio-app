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
import org.touchhome.bundle.api.repository.impl.UserRepository;

import java.security.Principal;

@Log4j2
@RestController
@RequestMapping("/rest/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    @GetMapping("status")
    public int getStatus(Principal user) {
        return user == null ? 401 : 200;
    }

    @PostMapping("login")
    public String login(@RequestBody Credentials credentials) {
        log.info("Login <{}>", credentials.getEmail());
        try {
            String username = credentials.getEmail();
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, credentials.getPassword()));
            return jwtTokenProvider.createToken(username, userRepository.getUser(username).getRoles());
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid username/password supplied");
        }
    }

    @Getter
    @Setter
    private static class Credentials {
        private String email;
        private String password;
    }
}
