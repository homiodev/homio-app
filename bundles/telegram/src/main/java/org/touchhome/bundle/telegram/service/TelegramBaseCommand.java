package org.touchhome.bundle.telegram.service;

import lombok.extern.log4j.Log4j2;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Log4j2
abstract class TelegramBaseCommand extends BotCommand {

    TelegramBaseCommand(String commandIdentifier, String description) {
        super(commandIdentifier, description);
    }

    void execute(AbsSender sender, SendMessage message, User user) {
        try {
            sender.execute(message);
        } catch (TelegramApiException ex) {
            log.error("Error", ex);
        }
    }
}
