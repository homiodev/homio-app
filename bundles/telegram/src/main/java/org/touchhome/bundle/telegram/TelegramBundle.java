package org.touchhome.bundle.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.telegram.service.TelegramService;

@Log4j2
@Component
@RequiredArgsConstructor
public class TelegramBundle implements BundleContext {

    private final TelegramService telegramService;

    public void init() {
        telegramService.postConstruct();
    }

    @Override
    public String getBundleId() {
        return "telegram";
    }

    @Override
    public int order() {
        return 200;
    }
}
