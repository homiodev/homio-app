package org.homio.app.rest;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;
import static org.homio.app.model.entity.user.UserBaseEntity.LOG_RESOURCE;
import static org.homio.app.model.entity.user.UserBaseEntity.SSH_RESOURCE_AUTHORIZE;
import static org.homio.app.model.entity.user.UserBaseEntity.log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.val;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.console.ConsolePluginEditor;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.util.CommonUtils;
import org.homio.app.LogService;
import org.homio.app.builder.ui.UIInputBuilderImpl;
import org.homio.app.console.LogsConsolePlugin;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.manager.common.impl.EntityContextUIImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.rest.ItemController.ActionModelRequest;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.ssh.SshBaseEntity;
import org.homio.app.ssh.SshProviderService;
import org.homio.app.ssh.SshProviderService.SshSession;
import org.homio.app.utils.UIFieldSelectionUtil;
import org.homio.app.utils.UIFieldUtils;
import org.json.JSONObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/console")
@RequiredArgsConstructor
public class ConsoleController implements ContextRefreshed {

    private final LogService logService;
    private final EntityContextImpl entityContext;
    private final ItemController itemController;
    private final Map<String, ConsolePlugin<?>> logsConsolePluginsMap = new HashMap<>();
    private List<ConsoleTab> logs;

    @Getter
    private static final Map<String, SshSession<?>> sessions = new HashMap<>();

    @Override
    public void onContextRefresh(EntityContext entityContext) {
        EntityContextUIImpl.consolePluginsMap.clear();

        logs = new ArrayList<>();
        for (String tab : logService.getTabs()) {
            logs.add(new ConsoleTab(tab, ConsolePlugin.RenderType.lines, null));
            logsConsolePluginsMap.put(tab, new LogsConsolePlugin(this.entityContext, logService, tab));
        }

        List<ConsolePlugin> consolePlugins = new ArrayList<>(this.entityContext.getBeansOfType(ConsolePlugin.class));
        Collections.sort(consolePlugins);
        for (ConsolePlugin<?> consolePlugin : consolePlugins) {
            EntityContextUIImpl.consolePluginsMap.put(consolePlugin.getName(), consolePlugin);
        }
        EntityContextUIImpl.consolePluginsMap.putAll(EntityContextUIImpl.customConsolePlugins);
    }

    @GetMapping("/tab/{tab}/content")
    public Object getTabContent(@PathVariable("tab") String tab) {
        ConsolePlugin<?> consolePlugin = EntityContextUIImpl.consolePluginsMap.get(tab);
        if (consolePlugin == null) {
            consolePlugin = logsConsolePluginsMap.get(tab);
            if (consolePlugin == null) {
                throw new IllegalArgumentException("Unable to find console plugin with name: " + tab);
            }
        }
        if (consolePlugin instanceof ConsolePluginTable<? extends HasEntityIdentifier> tableConsolePlugin) {
            Collection<? extends HasEntityIdentifier> baseEntities = tableConsolePlugin.getValue();
            Class<? extends HasEntityIdentifier> clazz = tableConsolePlugin.getEntityClass();

            return new EntityContent()
                    .setList(baseEntities)
                    .setActions(UIFieldUtils.fetchUIActionsFromClass(clazz, entityContext))
                    .setUiFields(UIFieldUtils.fillEntityUIMetadataList(clazz, entityContext));
        }
        return consolePlugin.getValue();
    }

    @GetMapping("/tab/{tab}/actions")
    public Collection<UIInputEntity> getConsoleTabActions(@PathVariable("tab") String tab) {
        UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
        ConsolePlugin<?> consolePlugin = EntityContextUIImpl.consolePluginsMap.get(tab);
        if (consolePlugin != null) {
            fetchUIHeaderActions(consolePlugin, uiInputBuilder);
        }
        return uiInputBuilder.buildAll();
    }

    @SneakyThrows
    @PostMapping("/tab/{tab}/action")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public ActionResponseModel executeAction(@PathVariable("tab") String tab, @RequestBody ActionModelRequest request) {
        String entityID = request.getEntityID();
        ConsolePlugin<?> consolePlugin = EntityContextUIImpl.consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginTable table) {
            HasEntityIdentifier identifier = table.findEntity(entityID);
            return itemController.executeAction(request, identifier, entityContext.getEntity(identifier.getEntityID()));
        }
        return consolePlugin.executeAction(entityID, request.getMetadata());
    }

    @GetMapping("/tab/{tab}/{entityID}/{fieldName}/options")
    public Collection<OptionModel> loadSelectOptions(
            @PathVariable("tab") String tab,
            @PathVariable("entityID") String entityID,
            @PathVariable("fieldName") String fieldName) {
        ConsolePlugin<?> consolePlugin = EntityContextUIImpl.consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginTable) {
            Collection<? extends HasEntityIdentifier> baseEntities = ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
            HasEntityIdentifier identifier =
                    baseEntities.stream().filter(e -> e.getEntityID().equals(entityID)).findAny().orElseThrow(
                            () -> new NotFoundException("Entity <" + entityID + "> not found"));
            return UIFieldSelectionUtil.loadOptions(
                    identifier, entityContext, fieldName, null, null, null, null);
        }
        return null;
    }

    @GetMapping("/tab")
    public Set<ConsoleTab> getTabs() {
        Set<ConsoleTab> tabs = new HashSet<>();
        if (entityContext.accessEnabled(LOG_RESOURCE)) {
            ConsoleTab logsTab = new ConsoleTab("logs", null, null);
            logs.forEach(logsTab::addChild);
            tabs.add(logsTab);
        }

        Map<String, ConsoleTab> parens = new HashMap<>();
        for (Map.Entry<String, ConsolePlugin<?>> entry :
                EntityContextUIImpl.consolePluginsMap.entrySet()) {
            if (entry.getKey().equals("icl")) {
                continue;
            }
            ConsolePlugin<?> consolePlugin = entry.getValue();
            if (!consolePlugin.isEnabled()) {
                continue;
            }
            String parentName = consolePlugin.getParentTab();
            ConsoleTab consoleTab = new ConsoleTab(entry.getKey(), consolePlugin.getRenderType(), consolePlugin.getOptions());
            consolePlugin.assembleOptions(consoleTab.getOptions());

            if (parentName != null) {
                parens.putIfAbsent(parentName, new ConsoleTab(parentName, null, consolePlugin.getOptions()));
                parens.get(parentName).addChild(consoleTab);
            } else {
                tabs.add(consoleTab);
            }
        }
        tabs.addAll(parens.values());

        // register still unavailable console plugins if any
        for (Entry<String, String> entry : EntityContextUIImpl.customConsolePluginNames.entrySet()) {
            if (entry.getValue().isEmpty() || entityContext.accessEnabled(entry.getValue())) {
                String pluginName = entry.getKey();
                if (tabs.stream().noneMatch(t -> t.name.equals(pluginName))) {
                    tabs.add(new ConsoleTab(pluginName, null, null));
                }
            }
        }

        return tabs;
    }

    @PostMapping("/{entityID}/ssh")
    @PreAuthorize(SSH_RESOURCE_AUTHORIZE)
    public SshProviderService.SshSession openSshSession(@PathVariable("entityID") String entityID) {
        log.info("Request to open ssh: {}", entityID);
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity instanceof SshBaseEntity) {
            SshProviderService service = ((SshBaseEntity<?, ?>) entity).getService();
            SshSession sshSession = service.openSshSession((SshBaseEntity) entity);
            if (sshSession != null) {
                sessions.put(sshSession.getToken(), sshSession);
            }
            return sshSession;
        }
        throw new IllegalArgumentException("Entity: " + entityID + " has to implement 'SshBaseEntity'");
    }

    @PostMapping("/ssh/{token}/resize")
    @PreAuthorize(SSH_RESOURCE_AUTHORIZE)
    public void resizeSshConsole(@PathVariable("token") String token, @RequestBody SshResizeRequest request) {
        SshSession<?> sshSession = sessions.get(token);
        if (sshSession != null) {
            ((SshProviderService) sshSession.getEntity().getService())
                    .resizeSshConsole(sshSession, request.cols);
        }
    }

    @DeleteMapping("/ssh/{token}")
    @PreAuthorize(SSH_RESOURCE_AUTHORIZE)
    public void closeSshSession(@PathVariable("token") String token) {
        SshSession<?> sshSession = sessions.remove(token);
        if (sshSession != null) {
            ((SshProviderService) sshSession.getEntity().getService()).closeSshSession(sshSession);
        }
    }

    /**
     * Get header actions for console plugin
     *
     * @param consolePlugin  - console plugin
     * @param uiInputBuilder - builder
     */
    @SneakyThrows
    static void fetchUIHeaderActions(
            ConsolePlugin<?> consolePlugin, UIInputBuilder uiInputBuilder) {
        val actionMap = consolePlugin.getHeaderActions();
        if (actionMap != null) {
            for (val entry : actionMap.entrySet()) {
                Class<? extends ConsoleHeaderSettingPlugin<?>> settingClass = entry.getValue();
                ((UIInputBuilderImpl) uiInputBuilder).addFireActionBeforeChange(entry.getKey(), CommonUtils.newInstance(settingClass).fireActionsBeforeChange(),
                        SettingEntity.getKey(settingClass), 0);
            }
        }
        if (consolePlugin instanceof ConsolePluginEditor) {
            Class<? extends ConsoleHeaderSettingPlugin<?>> nameHeaderAction = ((ConsolePluginEditor) consolePlugin).getFileNameHeaderAction();
            if (nameHeaderAction != null) {
                ((UIInputBuilderImpl) uiInputBuilder).addReferenceAction("name", SettingEntity.getKey(nameHeaderAction), 1);
            }
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @RequiredArgsConstructor
    private static class ConsoleTab {

        private final String name;
        private final ConsolePlugin.RenderType renderType;
        private List<ConsoleTab> children;
        private JSONObject options;

        public ConsoleTab(String name, ConsolePlugin.RenderType renderType, JSONObject options) {
            this(name, renderType);
            this.options = options == null ? new JSONObject() : options;
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
        Collection<UIInputEntity> actions;
    }

    @Setter
    public static class SshResizeRequest {

        private int cols;
    }
}
