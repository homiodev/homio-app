package org.touchhome.bundle.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.Type;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import java.util.Collections;

@Setter
@Entity
@Accessors(chain = true)
public class UserEntity extends BaseEntity<UserEntity> {

    private static UserEntity INSTANCE;

    @Getter
    private String userId;

    @Getter
    @JsonIgnore
    private String password;

    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @JsonIgnore
    @Getter
    private byte[] keystore;

    @JsonIgnore
    @Getter
    private String googleDriveAccessToken;

    @JsonIgnore
    @Getter
    private String googleDriveRefreshToken;

    @Getter
    private String lang = "en";

    @Enumerated(EnumType.STRING)
    @Getter
    private UserType userType = UserType.REGULAR;

    public static UserEntity get() {
        return INSTANCE;
    }

    public static void set(UserEntity userEntity) {
        INSTANCE = userEntity;
    }

    public boolean matchPassword(PasswordEncoder passwordEncoder, String rawPassword) {
        return passwordEncoder.matches(rawPassword, password);
    }

    public boolean matchPassword(String encodedPassword) {
        return this.password != null && this.password.equals(encodedPassword);
    }

    public UserDetails createUserDetails() {
        return new org.springframework.security.core.userdetails.User(userId, password, true,
                true, true, true, Collections.emptyList());
    }

    public boolean isPasswordNotSet() {
        return StringUtils.isEmpty(password);
    }

    public enum UserType {
        REGULAR, TELEGRAM
    }
}
