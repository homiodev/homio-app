package org.touchhome.bundle.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;

@Getter
@Setter
@Entity
@Accessors(chain = true)
public class UserEntity extends BaseEntity<UserEntity> {

    private static UserEntity INSTANCE;

    private String userId;

    @JsonIgnore
    private String password;

    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @JsonIgnore
    private byte[] keystore;

    @JsonIgnore
    private String googleDriveAccessToken;

    @JsonIgnore
    private String googleDriveRefreshToken;

    private String lang = "en";

    @Enumerated(EnumType.STRING)
    private UserType userType = UserType.REGULAR;

    public static UserEntity get() {
        return INSTANCE;
    }

    public static void set(UserEntity userEntity) {
        INSTANCE = userEntity;
    }

    public enum UserType {
        REGULAR, TELEGRAM
    }
}
