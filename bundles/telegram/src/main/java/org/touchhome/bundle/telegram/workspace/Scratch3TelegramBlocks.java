package org.touchhome.bundle.telegram.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.repository.impl.UserRepository;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.telegram.service.TelegramService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Component
@Scratch3Extension("telegram")
public class Scratch3TelegramBlocks extends Scratch3ExtensionBlocks {
    public static final String URL = "rest/v2/telegram/";

    private static final String USER = "USER";
    private static final String MESSAGE = "MESSAGE";
    private static final String LEVEL = "LEVEL";

    private final TelegramService telegramService;
    private final UserRepository userRepository;

    @JsonIgnore
    private final MenuBlock.StaticMenuBlock levelMenu;

    @JsonIgnore
    private final MenuBlock.ServerMenuBlock telegramUsersMenu;

    @JsonIgnore
    private final Scratch3Block sendMessageCommand;


    public Scratch3TelegramBlocks(TelegramService telegramService, EntityContext entityContext, UserRepository userRepository) {
        super("#73868c", entityContext);
        this.telegramService = telegramService;
        this.userRepository = userRepository;

        // Menu
        this.telegramUsersMenu = MenuBlock.ofServer("telegramUsersMenu", URL + "user/options", "All", "all");
        this.levelMenu = MenuBlock.ofStatic("levelMenu", Level.class);

        this.sendMessageCommand = Scratch3Block.ofHandler(0, "send_message", BlockType.command, "Send [MESSAGE] to user [USER]. [LEVEL]", this::sendMessageCommand);
        this.sendMessageCommand.addArgument(MESSAGE, ArgumentType.string);
        this.sendMessageCommand.addArgumentServerSelection(USER, this.telegramUsersMenu);
        this.sendMessageCommand.addArgument(LEVEL, ArgumentType.string, Level.info, this.levelMenu);

        this.postConstruct();
    }

    private void sendMessageCommand(WorkspaceBlock workspaceBlock) {
        String user = workspaceBlock.getMenuValue(USER, this.telegramUsersMenu, String.class);
        Level level = workspaceBlock.getMenuValue(LEVEL, this.levelMenu, Level.class);
        String message = workspaceBlock.getInputString(MESSAGE);

        List<UserEntity> users;
        if ("all".equals(user)) {
            users = this.userRepository.listAll().stream().filter(u -> u.getUserType() == UserEntity.UserType.TELEGRAM).collect(Collectors.toList());
            if (users.isEmpty()) {
                workspaceBlock.logWarn("Unable to find any registered users in telegram");
            }
        } else {
            users = new ArrayList<>();
            UserEntity userEntity = this.entityContext.getEntity(user);
            if (userEntity == null) {
                workspaceBlock.logError("Unable to find user with id <{}>", user);
            } else {
                users.add(userEntity);
            }
        }
        for (UserEntity userEntity : users) {
            workspaceBlock.logInfo("Send event to telegram user <{}>. Message <{}>", userEntity.getName(), message);
        }
        telegramService.sendMessage(users, level.format(message));
    }

    private enum Level {
        info, warn, error;

        public String format(String message) {
            return "[" + name().toUpperCase() + "]. " + message;
        }
    }
}
