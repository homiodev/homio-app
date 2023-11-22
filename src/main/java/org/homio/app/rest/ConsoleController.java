package org.homio.app.rest;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.model.entity.user.UserBaseEntity.LOG_RESOURCE;
import static org.homio.app.model.entity.user.UserBaseEntity.SSH_RESOURCE_AUTHORIZE;
import static org.homio.app.model.entity.user.UserBaseEntity.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URL;
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
import org.homio.api.Context;
import org.homio.api.ContextNetwork;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.console.ConsolePluginEditor;
import org.homio.api.console.ConsolePluginFrame;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
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
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextUIImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.rest.ItemController.ActionModelRequest;
import org.homio.app.setting.system.SystemFramesSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.ssh.SshBaseEntity;
import org.homio.app.ssh.SshProviderService;
import org.homio.app.ssh.SshProviderService.SshSession;
import org.homio.app.utils.OptionUtil;
import org.homio.app.utils.UIFieldUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
public class ConsoleController implements ContextCreated {

    private final LogService logService;
    private final ContextImpl context;
    private final ItemController itemController;
    private final Map<String, ConsolePlugin<?>> logsConsolePluginsMap = new HashMap<>();
    private final List<ConsoleTab> logs = new ArrayList<>();

    @Getter
    private static final Map<String, SshSession<?>> sessions = new HashMap<>();

    @Override
    public void onContextCreated(ContextImpl context) throws Exception {
        for (String tab : logService.getTabs()) {
            logs.add(new ConsoleTab(tab, ConsolePlugin.RenderType.lines, null));
            logsConsolePluginsMap.put(tab, new LogsConsolePlugin(this.context, logService, tab));
        }

        List<ConsolePlugin> consolePlugins = new ArrayList<>(this.context.getBeansOfType(ConsolePlugin.class));
        Collections.sort(consolePlugins);
        for (ConsolePlugin<?> consolePlugin : consolePlugins) {
            ContextUIImpl.consolePluginsMap.put(consolePlugin.getName(), consolePlugin);
        }

        ObjectNode nodes = context.setting().getValue(SystemFramesSetting.class);
        nodes.fields().forEachRemaining(entry -> {
            entry.getValue().fields().forEachRemaining(child -> {
                JsonNode node = child.getValue();
                String host = node.get("host").asText();
                String tabName = child.getKey();
                String targetHost = buildTargetHost(host, node.get("proxy").asBoolean());
                addFramePlugin(tabName, entry.getKey(), targetHost);
            });
        });
    }

    @GetMapping("/tab/{tab}/content")
    public Object getTabContent(@PathVariable("tab") String tab) {
        ConsolePlugin<?> consolePlugin = ContextUIImpl.getPlugin(tab);
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
                    .setActions(UIFieldUtils.fetchUIActionsFromClass(clazz, context))
                    .setUiFields(UIFieldUtils.fillEntityUIMetadataList(clazz, context));
        }
        return consolePlugin.getValue();
    }

    @GetMapping("/tab/{tab}/actions")
    public Collection<UIInputEntity> getConsoleTabActions(@PathVariable("tab") String tab) {
        UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
        ConsolePlugin<?> consolePlugin = ContextUIImpl.getPlugin(tab);
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
        ConsolePlugin<?> consolePlugin = ContextUIImpl.getPlugin(tab);
        if (consolePlugin instanceof ConsolePluginTable table) {
            HasEntityIdentifier identifier = table.findEntity(entityID);
            return itemController.executeAction(request, identifier, context.db().getEntity(identifier.getEntityID()));
        }
        return consolePlugin.executeAction(entityID, request.getParams());
    }

    @GetMapping("/tab/{tab}/{entityID}/{fieldName}/options")
    public Collection<OptionModel> loadSelectOptions(
            @PathVariable("tab") String tab,
            @PathVariable("entityID") String entityID,
            @PathVariable("fieldName") String fieldName) {
        ConsolePlugin<?> consolePlugin = ContextUIImpl.getPlugin(tab);
        if (consolePlugin instanceof ConsolePluginTable) {
            Collection<? extends HasEntityIdentifier> baseEntities = ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
            HasEntityIdentifier identifier =
                    baseEntities.stream().filter(e -> e.getEntityID().equals(entityID)).findAny().orElseThrow(
                        () -> NotFoundException.entityNotFound(entityID));
            return OptionUtil.loadOptions(identifier, context, fieldName, null, null, null);
        }
        return null;
    }

    @GetMapping("/tab")
    public Set<ConsoleTab> getTabs() {
        Set<ConsoleTab> tabs = new HashSet<>();
        if (context.accessEnabled(LOG_RESOURCE)) {
            ConsoleTab logsTab = new ConsoleTab("logs", null, null);
            logs.forEach(logsTab::addChild);
            tabs.add(logsTab);
        }

        Map<String, ConsoleTab> parens = new HashMap<>();
        for (Map.Entry<String, ConsolePlugin<?>> entry : ContextUIImpl.consolePluginsMap.entrySet()) {
            addConsolePlugin(entry, parens, tabs, false);
        }
        for (Map.Entry<String, ConsolePlugin<?>> entry : ContextUIImpl.consoleRemovablePluginsMap.entrySet()) {
            addConsolePlugin(entry, parens, tabs, true);
        }
        tabs.addAll(parens.values());

        // register still unavailable console plugins if any
        for (Entry<String, String> entry : ContextUIImpl.customConsolePluginNames.entrySet()) {
            if (entry.getValue().isEmpty() || context.accessEnabled(entry.getValue())) {
                String pluginName = entry.getKey();
                if (tabs.stream().noneMatch(t -> t.name.equals(pluginName))) {
                    tabs.add(new ConsoleTab(pluginName, null, null));
                }
            }
        }

        /*ObjectNode nodes = context.setting().getValue(SystemFramesSetting.class);
        Map<String, ConsoleTab> userTabs = new HashMap<>();
        nodes.fields().forEachRemaining(entry -> {
            ConsoleTab parentTab = userTabs.computeIfAbsent(entry.getKey(), s -> new ConsoleTab(entry.getKey(), null, null));
            entry.getValue().fields().forEachRemaining(child -> {
                String host = child.getValue().get("host").asText();
                if (child.getValue().get("proxy").asBoolean()) {
                    host = context.service().registerUrlProxy(host, host, builder -> {
                    });
                }
                parentTab.addChild(new ConsoleTab(child.getKey(), RenderType.frame,
                    new JSONObject().put("host", host)));
            });
        });
        tabs.addAll(userTabs.values());*/

        return tabs;
    }

    private static void addConsolePlugin(Entry<String, ConsolePlugin<?>> entry,
        Map<String, ConsoleTab> parens,
        Set<ConsoleTab> tabs,
        boolean removable) {
        if (entry.getKey().equals("icl")) {
            return;
        }
        ConsolePlugin<?> consolePlugin = entry.getValue();
        if (!consolePlugin.isEnabled()) {
            return;
        }
        String parentName = consolePlugin.getParentTab();
        ConsoleTab consoleTab = new ConsoleTab(entry.getKey(), consolePlugin.getRenderType(), consolePlugin.getOptions());
        if (removable) {
            consoleTab.setDeletable(true);
        }
        consolePlugin.assembleOptions(consoleTab.getOptions());

        if (parentName != null) {
            parens.putIfAbsent(parentName, new ConsoleTab(parentName, null, null));
            parens.get(parentName).addChild(consoleTab);
        } else {
            tabs.add(consoleTab);
        }
    }

    @PostMapping("/{entityID}/ssh")
    @PreAuthorize(SSH_RESOURCE_AUTHORIZE)
    public SshProviderService.SshSession openSshSession(@PathVariable("entityID") String entityID) {
        log.info("Request to open ssh: {}", entityID);
        BaseEntity entity = context.db().getEntity(entityID);
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

    @SneakyThrows
    @PostMapping("/frame")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void createFrame(@RequestBody CreateFrameRequest request) {
        URL url = new URL(request.host);
        ContextNetwork.ping(url.getHost(), url.getPort());
        if (context.ui().console().getRegisteredPlugin(request.name) != null) {
            throw new ServerException("Console plugin %s already exists".formatted(request.name));
        }
        ObjectNode nodes = context.setting().getValue(SystemFramesSetting.class);
        nodes.putIfAbsent(request.getParent(), OBJECT_MAPPER.createObjectNode());
        ObjectNode node = (ObjectNode) nodes.get(request.getParent());
        ObjectNode pluginData = OBJECT_MAPPER.createObjectNode()
                                             .put("proxy", request.proxy)
                                             .put("host", request.host);
        node.set(request.getName(), pluginData);
        context.setting().setValue(SystemFramesSetting.class, nodes);

        String targetHost = buildTargetHost(request.host, request.proxy);
        addFramePlugin(request.name, request.parent, targetHost);
    }

    private String buildTargetHost(String targetHost, boolean proxy) {
        if (proxy) {
            return context.service().registerUrlProxy(String.valueOf(Math.abs(targetHost.hashCode())), targetHost, builder -> {
            });
        }
        return targetHost;
    }

    @SneakyThrows
    @DeleteMapping("/frame/{parent}/{name}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public boolean removeFrame(@PathVariable("parent") String parent, @PathVariable("name") String name) {
        ObjectNode nodes = context.setting().getValue(SystemFramesSetting.class);
        if (!nodes.has(parent)) {
            throw new ServerException("No parent tab: " + parent + " found");
        }
        ObjectNode parentNode = (ObjectNode) nodes.get(parent);
        JsonNode pluginData = parentNode.remove(name);
        if (pluginData == null) {
            throw new ServerException("No tab: " + name + " found");
        }
        if (pluginData.has("proxyHost")) {
            context.service().unRegisterUrlProxy(pluginData.get("host").asText());
        }

        context.ui().console().unRegisterPlugin(name);
        if (parentNode.isEmpty()) {
            nodes.remove(parent);
        }

        context.setting().setValue(SystemFramesSetting.class, nodes);
        return nodes.isEmpty();
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
    public static class ConsoleTab {

        private final @NotNull String name;
        private final @Nullable ConsolePlugin.RenderType renderType;
        private boolean deletable;
        private List<ConsoleTab> children;
        private JSONObject options;

        public ConsoleTab(@NotNull String name, @Nullable ConsolePlugin.RenderType renderType, @Nullable JSONObject options) {
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

    @Getter
    @Setter
    public static class CreateFrameRequest {

        private String name;
        private String parent;
        private String host;
        private boolean proxy;
    }

    private void addFramePlugin(String host, String parent, String targetHost) {
        context.ui().console().registerPlugin(host, new ConsolePluginFrame() {

            @Override
            public @Nullable String getParentTab() {
                return parent;
            }

            @Override
            public @NotNull Context context() {
                return context;
            }

            @Override
            public FrameConfiguration getValue() {
                return new FrameConfiguration(targetHost);
            }
        }, true);
    }
}
