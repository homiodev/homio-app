package org.touchhome.app.auth;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class Credentials {

    @Email
    private String email;

    @NotEmpty
    private String password;

    private String op;
}
