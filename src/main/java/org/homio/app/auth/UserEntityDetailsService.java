package org.homio.app.auth;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.homio.app.model.entity.user.UserBaseEntity;
import org.homio.app.repository.device.AllDeviceRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserEntityDetailsService implements UserDetailsService {

    private final AllDeviceRepository deviceRepository;

    @Override
    public UserDetails loadUserByUsername(String name) {
        UserBaseEntity user = deviceRepository.getByIeeeAddressOrName(name);
        if (user == null) {
            throw new IllegalStateException("W.ERROR.USER_NOT_EXISTS_OR_WRONG_PASSWORD");
        }
        Set<String> roles = user.getRoles();
        return User
            .withUsername(user.getEntityID()) // entity id because it cached and fast to use from entityContext.getEntity(username)
            .password(user.getPassword().asString())
            .authorities(roles.toArray(new String[0]))
            .build();
    }
}
