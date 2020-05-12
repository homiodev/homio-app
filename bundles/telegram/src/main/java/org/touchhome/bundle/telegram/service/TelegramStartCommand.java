package org.touchhome.bundle.telegram.service;

import lombok.extern.log4j.Log4j2;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;

/**
 * https://github.com/rubenlagus/TelegramBots
 */
@Log4j2
public final class TelegramStartCommand extends TelegramBaseCommand {

    private final EntityContext entityContext;

    TelegramStartCommand(EntityContext entityContext) {
        super("start", "start using bot\n");
        this.entityContext = entityContext;
    }

    /**
     * реализованный метод класса BotCommand, в котором обрабатывается команда, введенная пользователем
     *
     * @param absSender - отправляет ответ пользователю
     * @param user      - пользователь, который выполнил команду
     * @param chat      - чат бота и пользователя
     * @param strings   - аргументы, переданные с командой
     */
    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {
        StringBuilder sb = new StringBuilder();

        SendMessage message = new SendMessage();
        message.setChatId(chat.getId().toString());

        UserEntity entity = entityContext.getEntity(UserEntity.PREFIX + user.getId());
        if (entity != null) {
            sb.append("User: <").append(entity.getName()).append("> already registered");
            log.info("Telegram user <{}> already registered", entity.getName());
        } else {
            entity = new UserEntity()
                    .setName(user.getFirstName())
                    .setUserType(UserEntity.UserType.TELEGRAM)
                    .setUserId(String.valueOf(user.getId()))
                    .setEntityID(UserEntity.PREFIX + user.getId());
            entityContext.save(entity);
            sb.append("User <").append(entity.getName()).append("> has been registered successfully.");
            log.info("Telegram user <{}> has been registered successfully.", entity.getName());
        }
        message.setText(sb.toString());
        execute(absSender, message, user);
    }
}
