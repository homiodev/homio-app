package org.touchhome.bundle.telegram.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.telegram.settings.TelegramBotNameSetting;
import org.touchhome.bundle.telegram.settings.TelegramBotTokenSetting;
import org.touchhome.bundle.telegram.settings.TelegramRestartBotButtonSetting;

import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@Log4j2
@Component
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramBotsApi botsApi = new TelegramBotsApi();
    private final DefaultBotOptions botOptions = ApiContext.getInstance(DefaultBotOptions.class);

    private final EntityContext entityContext;

    private BotSession telegramBot;
    private RestTemplate restTemplate = new RestTemplate();

    public void postConstruct() {
        ApiContextInitializer.init();
        entityContext.listenSettingValue(TelegramRestartBotButtonSetting.class, this::restart);
        start();
    }

    public void restart() {
        if (telegramBot != null && telegramBot.isRunning()) {
            telegramBot.stop();
        }
        this.start();
    }

    public void sendMessage(List<UserEntity> users, String message) {
        if (users != null && !users.isEmpty()) {
            String token = entityContext.getSettingValue(TelegramBotTokenSetting.class);
            for (UserEntity user : users) {
                restTemplate.postForObject("https://api.telegram.org/bot" + token + "/sendMessage", new SendMessageData(user.getUserId(), message), Object.class);
            }
        }
    }

    private void start() {
        try {
            if (isNotEmpty(entityContext.getSettingValue(TelegramBotNameSetting.class)) &&
                    isNotEmpty(entityContext.getSettingValue(TelegramBotTokenSetting.class))) {
                this.telegramBot = botsApi.registerBot(new TelegramBot(botOptions, entityContext));
                log.info("Telegram bot started");
                entityContext.sendInfoMessage("Telegram bot started");
            } else {
                log.warn("Telegram bot not started. Requires settings.");
                entityContext.sendInfoMessage("Telegram bot started. Requires settings.");

            }
        } catch (Exception ex) {
            entityContext.sendErrorMessage("Unable to start telegram bot: ", ex);
            log.error("Unable to start telegram bot", ex);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class SendMessageData {
        private String chat_id;
        private String text;
    }
}
