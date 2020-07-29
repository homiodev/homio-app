package org.touchhome.app.auth;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;

@Getter
@Setter
class Credentials {
    @Email
    private String email;

    @NotEmpty
    private String password;

    private String op;
}
