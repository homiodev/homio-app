package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.LogService;
import org.touchhome.app.console.LogsConsolePlugin;
import org.touchhome.app.console.NamedConsolePlugin;
import org.touchhome.app.json.UIActionDescription;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.app.service.ssh.SshProvider;
import org.touchhome.app.setting.console.ssh.ConsoleSshProviderSetting;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.console.ConsolePluginCommunicator;
import org.touchhome.bundle.api.console.ConsolePluginEditor;
import org.touchhome.bundle.api.console.ConsolePluginTable;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.json.ActionResponse;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.tritonus.share.ArraySet;

import java.util.*;

@RestController
@RequestMapping("/rest/console")
@RequiredArgsConstructor
public class ConsoleController {

    private final LogService logService;
    private final EntityContext entityContext;
    private final ApplicationContext applicationContext;
    private final Set<ConsoleTab> tabs = new ArraySet<>();
    @Getter
    private Map<String, ConsolePlugin<?>> consolePluginsMap = new HashMap<>();
    private Map<String, ConsolePlugin<?>> logsConsolePluginsMap = new HashMap<>();

    public void postConstruct() {
        this.consolePluginsMap.clear();
        this.tabs.clear();

        ConsoleTab log4j2Tab = new ConsoleTab("logs", null, null);
        for (String tab : logService.getTabs()) {
            log4j2Tab.addChild(new ConsoleTab(tab, ConsolePlugin.RenderType.lines, null));
            this.logsConsolePluginsMap.put(tab, new LogsConsolePlugin(logService, tab));
        }
        this.tabs.add(log4j2Tab);

        List<ConsolePlugin> consolePlugins = new ArrayList<>(entityContext.getBeansOfType(ConsolePlugin.class));
        Collections.sort(consolePlugins);
        for (ConsolePlugin consolePlugin : consolePlugins) {
            String bundleName = BundleEntryPoint.getBundleName(consolePlugin.getClass());
            if (bundleName == null) {
                if (!(consolePlugin instanceof NamedConsolePlugin)) {
                    throw new IllegalArgumentException("Unable to find ConsolePlugin name for class: "
                            + consolePlugin.getClass().getSimpleName());
                }
                bundleName = ((NamedConsolePlugin) consolePlugin).getName();
            }
            this.consolePluginsMap.put(bundleName, consolePlugin);
        }
    }

    @GetMapping("tab/{tab}/actions")
    public List<UIActionDescription> getConsoleTabActions(@PathVariable("tab") String tab) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        return consolePlugin == null ? Collections.emptyList() : ItemController.fetchUIHeaderActions(consolePlugin.getHeaderActions());
    }

    @GetMapping("tab/{tab}/content")
    public Object getTabContent(@PathVariable("tab") String tab) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        if (consolePlugin == null) {
            consolePlugin = logsConsolePluginsMap.get(tab);
            if (consolePlugin == null) {
                throw new IllegalArgumentException("Unable to find console plugin with name: " + tab);
            }
        }
        if (consolePlugin instanceof ConsolePluginTable) {
            ConsolePluginTable<? extends HasEntityIdentifier> tableConsolePlugin = (ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin;
            Collection<? extends HasEntityIdentifier> baseEntities = tableConsolePlugin.getValue();
            Class<? extends HasEntityIdentifier> clazz = tableConsolePlugin.getEntityClass();

            return new EntityContent()
                    .setList(baseEntities)
                    .setActions(ItemController.fetchUIActionsFromClass(clazz))
                    .setUiFields(UtilsController.fillEntityUIMetadataList(clazz));
        }
        return consolePlugin.getValue();
    }

    @PostMapping(value = "tab/{tab}/{entityID}/action")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public ActionResponse executeAction(@PathVariable("tab") String tab,
                                        @PathVariable("entityID") String entityID,
                                        @RequestBody UIActionDescription requestAction) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginTable) {
            Collection<? extends HasEntityIdentifier> baseEntities = ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
            HasEntityIdentifier identifier = baseEntities.stream().filter(e -> e.getEntityID().equals(entityID)).findAny().orElseThrow(() -> new NotFoundException("Entity <" + entityID + "> not found"));
            return ItemController.executeAction(requestAction, identifier, applicationContext, entityContext.getEntity(identifier.getEntityID()));
        } else if (consolePlugin instanceof ConsolePluginCommunicator) {
            return ((ConsolePluginCommunicator) consolePlugin).commandReceived(requestAction.getName());
        } else if (consolePlugin instanceof ConsolePluginEditor) {
            return ((ConsolePluginEditor) consolePlugin).save(
                    new ConsolePluginEditor.EditorContent(requestAction.getName(), requestAction.getMetadata().getString("content")));
        }
        throw new IllegalArgumentException("Unable to handle action for tab: " + tab);
    }

    @GetMapping("tab/{tab}/{entityID}/{fieldName}/options")
    public List<Option> loadSelectOptions(@PathVariable("tab") String tab,
                                          @PathVariable("entityID") String entityID,
                                          @PathVariable("fieldName") String fieldName) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginTable) {
            Collection<? extends HasEntityIdentifier> baseEntities = ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
            HasEntityIdentifier identifier = baseEntities.stream().filter(e -> e.getEntityID().equals(entityID))
                    .findAny().orElseThrow(() -> new NotFoundException("Entity <" + entityID + "> not found"));
            return ItemController.loadOptions(identifier, entityContext, fieldName);
        }
        return null;
    }

    @GetMapping("tab")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Set<ConsoleTab> getTabs() {
        Set<ConsoleTab> tabs = new HashSet<>(this.tabs);
        Map<String, ConsoleTab> parens = new HashMap<>();
        for (Map.Entry<String, ConsolePlugin<?>> entry : this.consolePluginsMap.entrySet()) {
            ConsolePlugin<?> consolePlugin = entry.getValue();
            String parentName = consolePlugin.getParentTab();
            if (consolePlugin.isEnabled()) {
                ConsoleTab consoleTab = new ConsoleTab(entry.getKey(), consolePlugin.getRenderType(), consolePlugin.getOptions());
                if (parentName != null) {
                    parens.putIfAbsent(parentName, new ConsoleTab(parentName, null, consolePlugin.getOptions()));
                    parens.get(parentName).addChild(consoleTab);
                } else {
                    tabs.add(consoleTab);
                }
            }
        }
        tabs.addAll(parens.values());
        return tabs;
    }

    @PostMapping("ssh")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public SshProvider.SshSession openSshSession() {
        return this.entityContext.setting().getValue(ConsoleSshProviderSetting.class).openSshSession();
    }

    @DeleteMapping("ssh/{token}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void closeSshSession(@PathVariable("token") String token) {
        this.entityContext.setting().getValue(ConsoleSshProviderSetting.class).closeSshSession(token);
    }

    @GetMapping("ssh/{token}")
    public SessionStatusModel getSshStatus(@PathVariable("token") String token) {
        return this.entityContext.setting().getValue(ConsoleSshProviderSetting.class).getSshStatus(token);
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    private static class ConsoleTab {
        private List<ConsoleTab> children;
        private String name;
        private ConsolePlugin.RenderType renderType;
        private JSONObject options;

        public ConsoleTab(String name, ConsolePlugin.RenderType renderType, JSONObject options) {
            this.name = name;
            this.renderType = renderType;
            this.options = options;
        }

        public void addChild(ConsoleTab consoleTab) {
            if (this.children == null) {
                this.children = new ArrayList<>();
            }
            this.children.add(consoleTab);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class EntityContent {
        Collection<?> list;
        List<EntityUIMetaData> uiFields;
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
