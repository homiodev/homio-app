package org.touchhome.bundle.api.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.UserEntity;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.util.HashMap;
import java.util.Map;

public class En {
    private static final En INSTANCE = new En();

    private final Map<String, ObjectNode> i18nLang = new HashMap<>();

    public static En get() {
        return INSTANCE;
    }

    public ObjectNode getLangJson(String lang) {
        if (EntityContext.isTestApplication() || !i18nLang.containsKey(lang)) {
            i18nLang.put(lang, TouchHomeUtils.readAndMergeJSON("i18n/" + lang + ".json", TouchHomeUtils.OBJECT_MAPPER.createObjectNode()));
        }
        return i18nLang.get(lang);
    }

    public String findPathText(String name) {
        ObjectNode langJson = getLangJson(UserEntity.get().getLang());
        return langJson.at("/" + name.replaceAll("\\.", "/")).textValue();
    }
}
