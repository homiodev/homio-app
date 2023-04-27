package org.homio.app.rest;

import static org.homio.bundle.api.util.Constants.ADMIN_ROLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.app.LogService;
import org.homio.app.builder.ui.UIInputBuilderImpl;
import org.homio.app.console.LogsConsolePlugin;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.manager.common.impl.EntityContextUIImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.ssh.SshBaseEntity;
import org.homio.app.ssh.SshProviderService;
import org.homio.app.utils.UIFieldSelectionUtil;
import org.homio.app.utils.UIFieldUtils;
import org.homio.bundle.api.console.ConsolePlugin;
import org.homio.bundle.api.console.ConsolePluginCommunicator;
import org.homio.bundle.api.console.ConsolePluginEditor;
import org.homio.bundle.api.console.ConsolePluginTable;
import org.homio.bundle.api.console.dependency.ConsolePluginRequireZipDependency;
import org.homio.bundle.api.entity.BaseEntity;
import org.homio.bundle.api.exception.NotFoundException;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.model.FileModel;
import org.homio.bundle.api.model.HasEntityIdentifier;
import org.homio.bundle.api.model.OptionModel;
import org.homio.bundle.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.homio.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.homio.bundle.api.ui.field.action.v1.UIInputEntity;
import org.homio.bundle.api.util.CommonUtils;
import org.json.JSONObject;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tritonus.share.ArraySet;

@RestController
@RequestMapping("/rest/console")
@RequiredArgsConstructor
public class ConsoleController implements ContextRefreshed {

    private final LogService logService;
    private final EntityContextImpl entityContext;
    private final ItemController itemController;
    private final Set<ConsoleTab> tabs = new ArraySet<>();
    private final Map<String, ConsolePlugin<?>> logsConsolePluginsMap = new HashMap<>();

    @SneakyThrows
    static void fetchUIHeaderActions(
        ConsolePlugin<?> consolePlugin, UIInputBuilder uiInputBuilder) {
        Map<String, Class<? extends ConsoleHeaderSettingPlugin<?>>> actionMap =
            consolePlugin.getHeaderActions();
        if (actionMap != null) {
            for (Map.Entry<String, Class<? extends ConsoleHeaderSettingPlugin<?>>> entry :
                actionMap.entrySet()) {
                Class<? extends ConsoleHeaderSettingPlugin<?>> settingClass = entry.getValue();
                ((UIInputBuilderImpl) uiInputBuilder)
                    .addFireActionBeforeChange(
                        entry.getKey(),
                        CommonUtils.newInstance(settingClass).fireActionsBeforeChange(),
                        SettingEntity.getKey(settingClass),
                        0);

                /* TODO: actions.add(new UIActionResponse(entry.getKey())
                .putOpt("fabc", CommonUtils.newInstance(settingClass).fireActionsBeforeChange())
                .putOpt("ref", SettingEntity.getKey(settingClass)));*/
            }
        }
        if (consolePlugin instanceof ConsolePluginEditor) {
            Class<? extends ConsoleHeaderSettingPlugin<?>> nameHeaderAction =
                ((ConsolePluginEditor) consolePlugin).getFileNameHeaderAction();
            if (nameHeaderAction != null) {
                ((UIInputBuilderImpl) uiInputBuilder)
                    .addReferenceAction("name", SettingEntity.getKey(nameHeaderAction), 1);
                // TODO: actions.add(new UIActionResponse("name").putOpt("ref",
                // SettingEntity.getKey(nameHeaderAction)));
            }
        }
    }

    @Override
    public void onContextRefresh() {
        EntityContextUIImpl.consolePluginsMap.clear();
        this.tabs.clear();

        ConsoleTab log4j2Tab = new ConsoleTab("logs", null, null);
        for (String tab : logService.getTabs()) {
            log4j2Tab.addChild(new ConsoleTab(tab, ConsolePlugin.RenderType.lines, null));
            this.logsConsolePluginsMap.put(
                tab, new LogsConsolePlugin(entityContext, logService, tab));
        }
        this.tabs.add(log4j2Tab);

        List<ConsolePlugin> consolePlugins =
            new ArrayList<>(this.entityContext.getBeansOfType(ConsolePlugin.class));
        Collections.sort(consolePlugins);
        for (ConsolePlugin<?> consolePlugin : consolePlugins) {
            EntityContextUIImpl.consolePluginsMap.put(consolePlugin.getName(), consolePlugin);
        }
        EntityContextUIImpl.consolePluginsMap.putAll(EntityContextUIImpl.customConsolePlugins);
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

    @GetMapping("/tab/{tab}/content")
    public Object getTabContent(@PathVariable("tab") String tab) {
        ConsolePlugin<?> consolePlugin = EntityContextUIImpl.consolePluginsMap.get(tab);
        if (consolePlugin == null) {
            consolePlugin = logsConsolePluginsMap.get(tab);
            if (consolePlugin == null) {
                throw new IllegalArgumentException(
                    "Unable to find console plugin with name: " + tab);
            }
        }
        if (consolePlugin instanceof ConsolePluginTable) {
            ConsolePluginTable<? extends HasEntityIdentifier> tableConsolePlugin =
                (ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin;
            Collection<? extends HasEntityIdentifier> baseEntities = tableConsolePlugin.getValue();
            Class<? extends HasEntityIdentifier> clazz = tableConsolePlugin.getEntityClass();

            return new EntityContent()
                .setList(baseEntities)
                .setActions(UIFieldUtils.fetchUIActionsFromClass(clazz, entityContext))
                .setUiFields(UIFieldUtils.fillEntityUIMetadataList(clazz, entityContext));
        }
        return consolePlugin.getValue();
    }

    @SneakyThrows
    @PostMapping("/tab/{tab}/action")
    @Secured(ADMIN_ROLE)
    public ActionResponseModel executeAction(
        @PathVariable("tab") String tab,
        @RequestBody ItemController.ActionRequestModel request) {
        String entityID = request.getEntityID();
        ConsolePlugin<?> consolePlugin = EntityContextUIImpl.consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginTable) {
            Collection<? extends HasEntityIdentifier> baseEntities =
                ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
            HasEntityIdentifier identifier =
                baseEntities.stream()
                            .filter(e -> e.getEntityID().equals(entityID))
                            .findAny()
                            .orElseThrow(
                                () ->
                                    new NotFoundException(
                                        "Entity <" + entityID + "> not found"));
            return itemController.executeAction(
                request, identifier, entityContext.getEntity(identifier.getEntityID()));
        } else if (consolePlugin instanceof ConsolePluginCommunicator) {
            return ((ConsolePluginCommunicator) consolePlugin).commandReceived(request.getName());
        } else if (consolePlugin instanceof ConsolePluginEditor) {
            if (request.getMetadata().has("glyph")) {
                return ((ConsolePluginEditor) consolePlugin)
                    .glyphClicked(request.getMetadata().getString("glyph"));
            }
            if (StringUtils.isNotEmpty(request.getName()) && request.getMetadata().has("content")) {
                return ((ConsolePluginEditor) consolePlugin)
                    .save(
                        new FileModel(
                            request.getName(),
                            request.getMetadata().getString("content"),
                            null,
                            false));
            }
        } else {
            return consolePlugin.executeAction(
                entityID, request.getMetadata(), request.getParams());
        }
        throw new IllegalArgumentException("Unable to handle action for tab: " + tab);
    }

    @PostMapping("/tab/{tab}/update")
    public void updateDependencies(@PathVariable("tab") String tab) {
        ConsolePlugin<?> consolePlugin = EntityContextUIImpl.consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginRequireZipDependency) {
            ConsolePluginRequireZipDependency dependency =
                (ConsolePluginRequireZipDependency) consolePlugin;
            if (dependency.requireInstallDependencies()) {
                entityContext
                    .bgp()
                    .runWithProgress(
                        "install-deps-" + dependency.getClass().getSimpleName(),
                        false,
                        progressBar ->
                            dependency.installDependency(entityContext, progressBar),
                        null,
                        () -> new RuntimeException("DOWNLOAD_DEPENDENCIES_IN_PROGRESS"));
            }
        }
    }

    @GetMapping("/tab/{tab}/{entityID}/{fieldName}/options")
    public Collection<OptionModel> loadSelectOptions(
        @PathVariable("tab") String tab,
        @PathVariable("entityID") String entityID,
        @PathVariable("fieldName") String fieldName) {
        ConsolePlugin<?> consolePlugin = EntityContextUIImpl.consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginTable) {
            Collection<? extends HasEntityIdentifier> baseEntities =
                ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
            HasEntityIdentifier identifier =
                baseEntities.stream()
                            .filter(e -> e.getEntityID().equals(entityID))
                            .findAny()
                            .orElseThrow(
                                () ->
                                    new NotFoundException(
                                        "Entity <" + entityID + "> not found"));
            return UIFieldSelectionUtil.loadOptions(
                identifier, entityContext, fieldName, null, null, null, null);
        }
        return null;
    }

    @GetMapping("/tab")
    public Set<ConsoleTab> getTabs() {
        Set<ConsoleTab> tabs = new HashSet<>(this.tabs);
        Map<String, ConsoleTab> parens = new HashMap<>();
        for (Map.Entry<String, ConsolePlugin<?>> entry :
            EntityContextUIImpl.consolePluginsMap.entrySet()) {
            if (entry.getKey().equals("icl")) {
                continue;
            }
            ConsolePlugin<?> consolePlugin = entry.getValue();
            String parentName = consolePlugin.getParentTab();
            if (consolePlugin.isEnabled()) {
                ConsoleTab consoleTab =
                    new ConsoleTab(
                        entry.getKey(),
                        consolePlugin.getRenderType(),
                        consolePlugin.getOptions());

                if (consolePlugin instanceof ConsolePluginRequireZipDependency) {
                    consoleTab
                        .getOptions()
                        .put(
                            "reqDeps",
                            ((ConsolePluginRequireZipDependency<?>) consolePlugin)
                                .requireInstallDependencies());
                }

                if (parentName != null) {
                    parens.putIfAbsent(
                        parentName,
                        new ConsoleTab(parentName, null, consolePlugin.getOptions()));
                    parens.get(parentName).addChild(consoleTab);
                } else {
                    tabs.add(consoleTab);
                }
            }
        }
        tabs.addAll(parens.values());

        // register still unavailable console plugins if any
        for (String pluginName : EntityContextUIImpl.customConsolePluginNames) {
            if (tabs.stream().noneMatch(t -> t.name.equals(pluginName))) {
                tabs.add(new ConsoleTab(pluginName, null, null));
            }
        }

        return tabs;
    }

    @PostMapping("/ssh")
    @Secured(ADMIN_ROLE)
    public SshProviderService.SshSession openSshSession(@RequestBody SshRequest request) {
        BaseEntity entity = entityContext.getEntity(request.getEntityID());
        if (entity instanceof SshBaseEntity) {
            SshProviderService service = ((SshBaseEntity<?, ?>) entity).getService();
            return service.openSshSession((SshBaseEntity) entity);
        }
        throw new IllegalArgumentException("Entity: " + request.getEntityID() + " has to implement 'SshBaseEntity'");
    }

    @DeleteMapping("/ssh")
    @Secured(ADMIN_ROLE)
    public void closeSshSession(@RequestBody SshRequest request) {
        BaseEntity entity = entityContext.getEntity(request.getEntityID());
        if (entity instanceof SshBaseEntity) {
            SshProviderService service = ((SshBaseEntity<?, ?>) entity).getService();
            service.closeSshSession(request.token, (SshBaseEntity) entity);
            return;
        }
        throw new IllegalArgumentException("Entity: " + request.getEntityID() + " has to implement 'SshBaseEntity'");
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

    @Getter
    @Setter
    public static class SshRequest {

        private String entityID;
        private String token;
    }
}
