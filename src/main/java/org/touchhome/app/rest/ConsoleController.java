package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.LogService;
import org.touchhome.app.console.LogsConsolePlugin;
import org.touchhome.app.console.NamedConsolePlugin;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.app.setting.console.ssh.ConsoleSshProviderSetting;
import org.touchhome.app.utils.UIFieldUtils;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.console.ConsolePluginCommunicator;
import org.touchhome.bundle.api.console.ConsolePluginEditor;
import org.touchhome.bundle.api.console.ConsolePluginTable;
import org.touchhome.bundle.api.console.dependency.ConsolePluginRequireZipDependency;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.model.FileModel;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.service.SshProvider;
import org.touchhome.bundle.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.touchhome.bundle.api.ui.field.action.UIActionResponse;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.tritonus.share.ArraySet;

import java.util.*;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;

@RestController
@RequestMapping("/rest/console")
@RequiredArgsConstructor
public class ConsoleController {

    private final LogService logService;
    private final EntityContext entityContext;
    private final Set<ConsoleTab> tabs = new ArraySet<>();
    @Getter
    private Map<String, ConsolePlugin<?>> consolePluginsMap = new HashMap<>();
    private Map<String, ConsolePlugin<?>> logsConsolePluginsMap = new HashMap<>();

    @SneakyThrows
    static List<UIActionResponse> fetchUIHeaderActions(ConsolePlugin<?> consolePlugin) {
        List<UIActionResponse> actions = new ArrayList<>();
        Map<String, Class<? extends ConsoleHeaderSettingPlugin<?>>> actionMap = consolePlugin.getHeaderActions();
        if (actionMap != null) {
            for (Map.Entry<String, Class<? extends ConsoleHeaderSettingPlugin<?>>> entry : actionMap.entrySet()) {
                Class<? extends ConsoleHeaderSettingPlugin<?>> settingClass = entry.getValue();
                actions.add(new UIActionResponse(entry.getKey())
                        .putOpt("fabc", TouchHomeUtils.newInstance(settingClass).fireActionsBeforeChange())
                        .putOpt("ref", SettingEntity.getKey(settingClass)));
            }
        }
        if (consolePlugin instanceof ConsolePluginEditor) {
            Class<? extends ConsoleHeaderSettingPlugin<?>> nameHeaderAction = ((ConsolePluginEditor) consolePlugin).getFileNameHeaderAction();
            if (nameHeaderAction != null) {
                actions.add(new UIActionResponse("name").putOpt("ref", SettingEntity.getKey(nameHeaderAction)));
            }
        }
        return actions;
    }

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
            String bundleName = consolePlugin.getEntityID();
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

    @GetMapping("/tab/{tab}/actions")
    public List<UIActionResponse> getConsoleTabActions(@PathVariable("tab") String tab) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        return consolePlugin == null ? Collections.emptyList() : fetchUIHeaderActions(consolePlugin);
    }

    @GetMapping("/tab/{tab}/content")
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
                    .setActions(UIFieldUtils.fetchUIActionsFromClass(clazz))
                    .setUiFields(UIFieldUtils.fillEntityUIMetadataList(clazz));
        }
        return consolePlugin.getValue();
    }

    @PostMapping("/tab/{tab}/{entityID}/action")
    @Secured(ADMIN_ROLE)
    public ActionResponseModel executeAction(@PathVariable("tab") String tab,
                                             @PathVariable("entityID") String entityID,
                                             @RequestBody ItemController.ActionRequestModel actionRequestModel) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginTable) {
            Collection<? extends HasEntityIdentifier> baseEntities = ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
            HasEntityIdentifier identifier = baseEntities.stream().filter(e -> e.getEntityID().equals(entityID)).findAny().orElseThrow(() -> new NotFoundException("Entity <" + entityID + "> not found"));
            return ItemController.executeAction(entityContext, actionRequestModel, identifier, entityContext.getEntity(identifier.getEntityID()));
        } else if (consolePlugin instanceof ConsolePluginCommunicator) {
            return ((ConsolePluginCommunicator) consolePlugin).commandReceived(actionRequestModel.getName());
        } else if (consolePlugin instanceof ConsolePluginEditor) {
            if (actionRequestModel.getMetadata().has("glyph")) {
                return ((ConsolePluginEditor) consolePlugin).glyphClicked(actionRequestModel.getMetadata().getString("glyph"));
            }
            if (StringUtils.isNotEmpty(actionRequestModel.getName()) && actionRequestModel.getMetadata().has("content")) {
                return ((ConsolePluginEditor) consolePlugin).save(
                        new FileModel(actionRequestModel.getName(), actionRequestModel.getMetadata().getString("content"), null, false));
            }
        }
        throw new IllegalArgumentException("Unable to handle action for tab: " + tab);
    }

    @PostMapping("/tab/{tab}/update")
    public void updateDependencies(@PathVariable("tab") String tab) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginRequireZipDependency) {
            ConsolePluginRequireZipDependency dependency = (ConsolePluginRequireZipDependency) consolePlugin;
            if (dependency.requireInstallDependencies()) {
                entityContext.bgp().runWithProgress("install-deps-" + dependency.getClass().getSimpleName(),
                        false, progressBar -> dependency.installDependency(entityContext, progressBar), null,
                        () -> new RuntimeException("DOWNLOAD_DEPENDENCIES_IN_PROGRESS"));
            }
        }
    }

    @GetMapping("/tab/{tab}/{entityID}/{fieldName}/options")
    public Collection<OptionModel> loadSelectOptions(@PathVariable("tab") String tab,
                                                     @PathVariable("entityID") String entityID,
                                                     @PathVariable("fieldName") String fieldName) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        if (consolePlugin instanceof ConsolePluginTable) {
            Collection<? extends HasEntityIdentifier> baseEntities = ((ConsolePluginTable<? extends HasEntityIdentifier>) consolePlugin).getValue();
            HasEntityIdentifier identifier = baseEntities.stream().filter(e -> e.getEntityID().equals(entityID))
                    .findAny().orElseThrow(() -> new NotFoundException("Entity <" + entityID + "> not found"));
            return UIFieldUtils.loadOptions(identifier, entityContext, fieldName);
        }
        return null;
    }

    @GetMapping("/tab")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Set<ConsoleTab> getTabs() {
        Set<ConsoleTab> tabs = new HashSet<>(this.tabs);
        Map<String, ConsoleTab> parens = new HashMap<>();
        for (Map.Entry<String, ConsolePlugin<?>> entry : this.consolePluginsMap.entrySet()) {
            if (entry.getKey().equals("icl")) {
                continue;
            }
            ConsolePlugin<?> consolePlugin = entry.getValue();
            String parentName = consolePlugin.getParentTab();
            if (consolePlugin.isEnabled()) {
                ConsoleTab consoleTab = new ConsoleTab(entry.getKey(), consolePlugin.getRenderType(), consolePlugin.getOptions());

                if (consolePlugin instanceof ConsolePluginRequireZipDependency) {
                    consoleTab.getOptions().put("reqDeps", ((ConsolePluginRequireZipDependency<?>) consolePlugin).requireInstallDependencies());
                }

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

    @PostMapping("/ssh")
    @Secured(ADMIN_ROLE)
    public SshProvider.SshSession openSshSession() {
        return this.entityContext.setting().getValue(ConsoleSshProviderSetting.class).openSshSession();
    }

    @DeleteMapping("/ssh/{token}")
    @Secured(ADMIN_ROLE)
    public void closeSshSession(@PathVariable("token") String token) {
        this.entityContext.setting().getValue(ConsoleSshProviderSetting.class).closeSshSession(token);
    }

    @GetMapping("/ssh/{token}")
    public SshProvider.SessionStatusModel getSshStatus(@PathVariable("token") String token) {
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
        List<UIActionResponse> actions;
    }
}
