package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.bundle.api.converter.JSONConverter;
import org.homio.bundle.api.converter.StringSetConverter;
import org.homio.bundle.api.entity.BaseEntity;
import org.homio.bundle.api.entity.HasJsonData;
import org.homio.bundle.api.entity.UserEntity;
import org.homio.bundle.api.model.JSON;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "user_entity")
@Accessors(chain = true)
public final class UserEntityImpl extends BaseEntity<UserEntityImpl> implements UserEntity, HasJsonData {

    public static final String PREFIX = "u_";

    @Getter
    @Setter
    private String email;

    @Getter
    @JsonIgnore
    private String password;

    @Getter
    @Setter
    private String lang = "en";

    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    private UserType userType;

    @Getter
    @Setter
    @Lob
    @Column(length = 1_000_000)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData;

    @Getter
    @Setter
    @Column(nullable = false)
    @Convert(converter = StringSetConverter.class)
    private Set<String> roles;

    public boolean matchPassword(String password, PasswordEncoder passwordEncoder) {
        return this.password != null && (this.password.equals(password) ||
            (passwordEncoder != null && passwordEncoder.matches(password, this.password)));
    }

    public UserEntityImpl setPassword(String password, PasswordEncoder passwordEncoder) {
        if (passwordEncoder != null) {
            try {
                passwordEncoder.upgradeEncoding(password);
            } catch (Exception ex) {
                password = passwordEncoder.encode(password);
            }
        }
        this.password = password;
        return this;
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return "User";
    }
}
