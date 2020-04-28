package org.touchhome.bundle.telegram;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequestMapping("/rest/v2/telegram")
@AllArgsConstructor
public class TelegramController {

    private final UserRepository userRepository;

    @GetMapping("user/options")
    public List<Option> getRegisteredUsers() {
        return userRepository.listAll().stream()
                .filter(u -> u.getUserType() == UserEntity.UserType.TELEGRAM)
                .map(u -> Option.of(u.getEntityID(), u.getName()))
                .collect(Collectors.toList());
    }
}
