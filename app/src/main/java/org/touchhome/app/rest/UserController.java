package org.touchhome.app.rest;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;

@RestController
@RequestMapping("/rest/user")
@AllArgsConstructor
public class UserController {

    private final EntityContext entityContext;

    @GetMapping("")
    public UserEntity getUser() {
        return entityContext.getEntity(UserRepository.DEFAULT_USER_ID);
    }
}
