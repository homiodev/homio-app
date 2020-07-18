package org.touchhome.bundle.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Type;
import org.touchhome.bundle.api.util.SslUtil;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Setter
@Entity
@Accessors(chain = true)
public class UserEntity extends BaseEntity<UserEntity> {

    public static final String PREFIX = "u_";

    public static final String ADMIN_USER = PREFIX + "user";

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

    @Getter
    private Date keystoreDate;

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

    public boolean matchPassword(String encodedPassword) {
        return this.password != null && this.password.equals(encodedPassword);
    }

    public boolean isPasswordNotSet() {
        return StringUtils.isEmpty(password);
    }

    public List<Role> getRoles() {
        return Collections.singletonList(Role.ROLE_ADMIN);
    }

    public UserEntity setKeystore(byte[] keystore) {
        this.keystore = keystore;
        SslUtil.validateKeyStore(keystore, password);
        this.keystoreDate = new Date();
        return this;
    }

    public enum UserType {
        REGULAR, TELEGRAM
    }
}
