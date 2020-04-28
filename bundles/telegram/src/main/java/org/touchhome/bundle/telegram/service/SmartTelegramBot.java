package org.touchhome.bundle.telegram.service;

import lombok.extern.log4j.Log4j2;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.telegram.settings.TelegramBotNameSetting;
import org.touchhome.bundle.telegram.settings.TelegramBotTokenSetting;

@Log4j2
public final class SmartTelegramBot extends TelegramLongPollingCommandBot {

    private final EntityContext entityContext;

    SmartTelegramBot(DefaultBotOptions botOptions, EntityContext entityContext) {
        super(botOptions, true);
        this.entityContext = entityContext;

        register(new TelegramStartCommand(entityContext));

        // обработка неизвестной команды
        log.info("Registering default action'...");
        registerDefaultAction(((absSender, message) -> {

            log.warn("Telegram User {} is trying to execute unknown command '{}'.", message.getFrom().getId(), message.getText());

            SendMessage text = new SendMessage();
            text.setChatId(message.getChatId());
            text.setText(message.getText() + " command not found!");

            try {
                absSender.execute(text);
            } catch (TelegramApiException e) {
                log.error("Error while replying unknown command to user {}.", message.getFrom(), e);
            }
        }));
    }

    @Override
    public String getBotUsername() {
        return this.entityContext.getSettingValue(TelegramBotNameSetting.class);
    }

    @Override
    public String getBotToken() {
        return this.entityContext.getSettingValue(TelegramBotTokenSetting.class);
    }

    // handle message not started with '/'
    @Override
    public void processNonCommandUpdate(Update update) {
        log.info("Processing non-command update...");
    }
}
