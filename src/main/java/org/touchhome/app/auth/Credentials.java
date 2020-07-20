package org.touchhome.app.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Credentials {
    private String email;
    private String password;
    private String op;
}
