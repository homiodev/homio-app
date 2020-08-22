package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.LogService;
import org.touchhome.app.json.UIActionDescription;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.app.service.ssh.SshProvider;
import org.touchhome.app.setting.console.ssh.ConsoleSshProviderSetting;
import org.touchhome.bundle.api.BundleEntrypoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.tritonus.share.ArraySet;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rest/console")
@RequiredArgsConstructor
public class ConsoleController {

    private final LogService logService;
    private final EntityContext entityContext;
    private final ApplicationContext applicationContext;
    private final Set<Option> tabs = new ArraySet<>();

    @Getter
    private Map<String, ConsolePlugin> consolePluginsMap = new HashMap<>();

    public void postConstruct() {
        this.consolePluginsMap.clear();
        this.tabs.clear();

        this.tabs.addAll(logService.getTabs().stream().map(l -> Option.key(l).addJson("type", "log")).collect(Collectors.toList()));
        List<ConsolePlugin> consolePlugins = new ArrayList<>(entityContext.getBeansOfType(ConsolePlugin.class));
        Collections.sort(consolePlugins);
        for (ConsolePlugin consolePlugin : consolePlugins) {
            String bundleName = BundleEntrypoint.getBundleName(consolePlugin.getClass());
            this.consolePluginsMap.put(bundleName, consolePlugin);
        }
    }

    @GetMapping("{tab}/{type}/content")
    public Object getTabContent(@PathVariable("tab") String tab, @PathVariable("type") String type) {
        switch (type) {
            case "log":
                return this.logService.getLogs(tab);
            case "bundle":
                ConsolePlugin consolePlugin = consolePluginsMap.get(tab);
                List<String> list = consolePlugin.drawPlainString();
                if (list != null) {
                    return list;
                }
                List<? extends HasEntityIdentifier> baseEntities = consolePlugin.drawEntity();
                Class<? extends HasEntityIdentifier> clazz = baseEntities != null && !baseEntities.isEmpty() ? baseEntities.get(0).getClass() : null;
                return new EntityContent()
                        .setList(baseEntities)
                        .setActions(ItemController.fetchUIActionsFromClass(clazz))
                        .setHeaderActions(ItemController.fetchUIHeaderActions(consolePlugin.getHeaderActions()))
                        .setUiFields(UtilsController.fillEntityUIMetadataList(clazz));
        }
        return null;
    }

    @PostMapping(value = "{tab}/{type}/{entityID}/action")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public Object executeAction(@PathVariable("tab") String tab, @PathVariable("type") String type,
                                @PathVariable("entityID") String entityID,
                                @RequestBody UIActionDescription uiActionDescription) {
        switch (type) {
            case "bundle": {
                ConsolePlugin consolePlugin = consolePluginsMap.get(tab);
                List<? extends HasEntityIdentifier> baseEntities = consolePlugin.drawEntity();
                HasEntityIdentifier identifier = baseEntities.stream().filter(e -> e.getEntityID().equals(entityID)).findAny().orElseThrow(() -> new NotFoundException("Entity <" + entityID + "> not found"));
                return ItemController.executeAction(uiActionDescription, identifier, applicationContext, entityContext.getEntity(identifier.getEntityID()));
            }
        }
        throw new IllegalStateException("Unable to find handler");
    }

    @GetMapping("{tab}/{type}/{entityID}/options")
    public List loadSelectOptions(@PathVariable("tab") String tab,
                                  @PathVariable("type") String type,
                                  @PathVariable("entityID") String entityID,
                                  @RequestParam("selectOptionMethod") String selectOptionMethod) {
        switch (type) {
            case "bundle": {
                ConsolePlugin consolePlugin = consolePluginsMap.get(tab);
                List<? extends HasEntityIdentifier> baseEntities = consolePlugin.drawEntity();
                HasEntityIdentifier identifier = baseEntities.stream().filter(e -> e.getEntityID().equals(entityID))
                        .findAny().orElseThrow(() -> new NotFoundException("Entity <" + entityID + "> not found"));
                Method method = TouchHomeUtils.findRequreMethod(identifier.getClass(), selectOptionMethod);
                return (List) ItemController.executeMethodAction(method, identifier, applicationContext, null);
            }
        }
        throw new IllegalStateException("Unable to find handler");
    }

    @GetMapping("tab")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Set<Option> getTabs() {
        Set<Option> options = new LinkedHashSet<>(this.tabs);
        for (Map.Entry<String, ConsolePlugin> entry : this.consolePluginsMap.entrySet()) {
            if (entry.getValue().isEnabled()) {
                options.add(Option.key(entry.getKey()).addJson("type", "bundle"));
            }
        }
        return options;
    }

    @PostMapping("ssh")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public SshProvider.SshSession openSshSession() {
        return this.entityContext.getSettingValue(ConsoleSshProviderSetting.class).openSshSession();
    }

    @DeleteMapping("ssh/{token}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void closeSshSession(@PathVariable("token") String token) {
        this.entityContext.getSettingValue(ConsoleSshProviderSetting.class).closeSshSession(token);
    }

    @GetMapping("ssh/{token}")
    public SessionStatusModel getSshStatus(@PathVariable("token") String token) {
        return this.entityContext.getSettingValue(ConsoleSshProviderSetting.class).getSshStatus(token);
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class EntityContent {
        List list;
        List<EntityUIMetaData> uiFields;
        List<UIActionDescription> headerActions;
        List<UIActionDescription> actions;
    }

    @Getter
    @Setter
    public static class SessionStatusModel {
        private boolean closed;
        private String closed_at;
        private String created_at;
        private String disconnected_at;
        private String ssh_cmd_fmt;
        private String ws_url_fmt;
    }
}
