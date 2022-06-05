package org.touchhome.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONObject;
import org.springframework.data.util.Pair;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.touchhome.app.manager.ImageService;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.EntityManager;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.app.repository.device.AllDeviceRepository;
import org.touchhome.app.setting.system.SystemShowEntityCreateTimeSetting;
import org.touchhome.app.setting.system.SystemShowEntityUpdateTimeSetting;
import org.touchhome.app.utils.InternalUtil;
import org.touchhome.app.utils.UIFieldUtils;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.ImageEntity;
import org.touchhome.bundle.api.entity.dependency.DependencyExecutableInstaller;
import org.touchhome.bundle.api.entity.dependency.RequireExecutableDependency;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.model.HasPosition;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.service.EntityService;
import org.touchhome.bundle.api.ui.UISidebarButton;
import org.touchhome.bundle.api.ui.UISidebarChildren;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIFilterOptions;
import org.touchhome.bundle.api.ui.field.action.HasDynamicContextMenuActions;
import org.touchhome.bundle.api.ui.field.action.UIActionButton;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuAction;
import org.touchhome.bundle.api.ui.field.action.UIContextMenuUploadAction;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.ui.field.selection.dynamic.DynamicParameterFields;
import org.touchhome.bundle.api.ui.field.selection.dynamic.SelectionWithDynamicParameterFields;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.util.CommonUtils;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.awt.image.BufferedImage;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;

@SuppressWarnings({"rawtypes", "unchecked"})
@Log4j2
@RestController
@RequestMapping("/rest/item")
@RequiredArgsConstructor
public class ItemController {

    private static final Map<String, List<Class<? extends BaseEntity>>> typeToEntityClassNames = new HashMap<>();
    private final Map<String, TypeToRequireDependenciesContext> typeToRequireDependencies = new HashMap<>();
    private final Map<String, List<ItemContext>> itemsBootstrapContextMap = new HashMap<>();

    private final ObjectMapper objectMapper;
    private final EntityContextImpl entityContext;
    private final EntityManager entityManager;
    private final ClassFinder classFinder;
    private final ImageService imageService;

    private Map<String, Class<? extends BaseEntity>> baseEntitySimpleClasses;
    private Map<Class<? extends UIActionHandler>, UIActionHandler> sidebarClassButtonToInstance = new HashMap<>();

    @RequiredArgsConstructor
    private static class TypeToRequireDependenciesContext {
        private final Class<? extends BaseEntity> typeClass;
        private final List<DependencyExecutableInstaller> installers;
    }

    public <T extends UIActionHandler> T getOrCreateUIActionHandler(Class<T> actionHandlerClass) {
        return (T) sidebarClassButtonToInstance.computeIfAbsent(actionHandlerClass, handlerClass ->
                entityContext.getBean((Class<UIActionHandler>) handlerClass, () -> CommonUtils.newInstance(handlerClass)));
    }

    @SneakyThrows
    public ActionResponseModel executeAction(ActionRequestModel actionRequestModel,
                                             Object actionHolder, BaseEntity actionEntity) {
        for (Method method : MethodUtils.getMethodsWithAnnotation(actionHolder.getClass(), UIContextMenuAction.class)) {
            UIContextMenuAction menuAction = method.getDeclaredAnnotation(UIContextMenuAction.class);
            if (menuAction.value().equals(actionRequestModel.getName())) {
                return executeMethodAction(method, actionHolder, entityContext, actionEntity, actionRequestModel.getParams());
            }
        }
        for (Method method : MethodUtils.getMethodsWithAnnotation(actionHolder.getClass(), UIContextMenuUploadAction.class)) {
            UIContextMenuUploadAction menuAction = method.getDeclaredAnnotation(UIContextMenuUploadAction.class);
            if (menuAction.value().equals(actionRequestModel.getName())) {
                return executeMethodAction(method, actionHolder, entityContext, actionEntity, actionRequestModel.getParams());
            }
        }
        // in case when action attached to field or method
        if (actionRequestModel.metadata != null && actionRequestModel.metadata.has("field")) {
            String fieldName = actionRequestModel.metadata.getString("field");

            AccessibleObject field = Optional.ofNullable((AccessibleObject)
                            FieldUtils.getField(actionHolder.getClass(), fieldName, true))
                    .orElse(InternalUtil.findMethodByName(actionHolder.getClass(), fieldName));
            if (field != null) {
                for (UIActionButton actionButton : field.getDeclaredAnnotationsByType(UIActionButton.class)) {
                    if (actionButton.name().equals(actionRequestModel.name)) {
                        return getOrCreateUIActionHandler(actionButton.actionHandler()).handleAction(entityContext,
                                actionRequestModel.params);
                    }
                }
            }
        }
        if (actionHolder instanceof HasDynamicContextMenuActions) {
            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            ((HasDynamicContextMenuActions) actionHolder).assembleActions(uiInputBuilder);

            UIActionHandler actionHandler = uiInputBuilder.findActionHandler(actionRequestModel.name);
            if (actionHandler != null) {
                if (!actionHandler.isEnabled(entityContext)) {
                    throw new IllegalArgumentException("Unable to invoke disabled action");
                }
                actionHandler.handleAction(entityContext, actionRequestModel.params);
                return null;
            }
        }
        throw new IllegalArgumentException("Execution method name: <" + actionRequestModel.getName() + "> not implemented");
    }

    @SneakyThrows
    static ActionResponseModel executeMethodAction(Method method, Object actionHolder,
                                                   EntityContext entityContext,
                                                   BaseEntity actionEntity, JSONObject params) {
        List<Object> objects = new ArrayList<>();
        for (AnnotatedType parameterType : method.getAnnotatedParameterTypes()) {
            if (actionHolder.getClass().isAssignableFrom((Class) parameterType.getType())) {
                objects.add(actionHolder);
            } else if (BaseEntity.class.isAssignableFrom((Class) parameterType.getType())) {
                objects.add(actionEntity);
            } else if (JSONObject.class.isAssignableFrom((Class<?>) parameterType.getType())) {
                objects.add(params);
            } else {
                objects.add(entityContext.getBean((Class) parameterType.getType()));
            }
        }
        method.setAccessible(true);
        return (ActionResponseModel) method.invoke(actionHolder, objects.toArray());
    }

    public void postConstruct() {
        List<Class<? extends BaseEntity>> baseEntityClasses = classFinder.getClassesWithParent(BaseEntity.class, null, null);
        this.baseEntitySimpleClasses = baseEntityClasses.stream().collect(Collectors.toMap(Class::getSimpleName, s -> s));

        for (Class<? extends BaseEntity> baseEntityClass : baseEntityClasses) {
            Class<?> cursor = baseEntityClass.getSuperclass();
            while (!cursor.getSimpleName().equals(BaseEntity.class.getSimpleName())) {
                this.baseEntitySimpleClasses.put(cursor.getSimpleName(), (Class<? extends BaseEntity>) cursor);
                cursor = cursor.getSuperclass();
            }
        }
        // invalidate UIField cache scenarios
        entityContext.setting().listenValue(SystemShowEntityCreateTimeSetting.class, "invalidate-uifield-createTime-cache",
                this.itemsBootstrapContextMap::clear);
        entityContext.setting().listenValue(SystemShowEntityUpdateTimeSetting.class, "invalidate-uifield-updateTime-cache",
                this.itemsBootstrapContextMap::clear);
    }

    @GetMapping("/{type}/context")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<ItemContext> getItemsBootstrapContext(@PathVariable("type") String type,
                                                      @RequestParam(value = "subType", required = false) String subType) {
        String key = type + subType;
        // itemsBootstrapContextMap.clear(); // TODO: remove this
        itemsBootstrapContextMap.computeIfAbsent(key, s -> {
            List<ItemContext> itemContexts = new ArrayList<>();

            for (Class<?> classType : findAllClassImplementationsByType(type)) {
                List<EntityUIMetaData> entityUIMetaData = UIFieldUtils.fillEntityUIMetadataList(classType, new HashSet<>());
                if (subType != null && subType.contains(":")) {
                    String[] bundleAndClassName = subType.split(":");
                    Object subClassObject =
                            entityContext.getBeanOfBundleBySimpleName(bundleAndClassName[0], bundleAndClassName[1]);
                    List<EntityUIMetaData> subTypeFieldMetadata =
                            UIFieldUtils.fillEntityUIMetadataList(subClassObject, new HashSet<>());
                    // add 'cutFromJson' because custom fields must be fetched from json parameter (uses first available json
                    // parameter)
                    for (EntityUIMetaData data : subTypeFieldMetadata) {
                        data.setTypeMetaData(
                                new JSONObject(StringUtils.defaultString(data.getTypeMetaData(), "{}")).put("cutFromJson", true)
                                        .toString());
                    }
                    entityUIMetaData.addAll(subTypeFieldMetadata);
                }
                if (!entityContext.setting().getValue(SystemShowEntityCreateTimeSetting.class)) {
                    entityUIMetaData.removeIf(field -> field.getEntityName().equals("creationTime"));
                }
                if (!entityContext.setting().getValue(SystemShowEntityUpdateTimeSetting.class)) {
                    entityUIMetaData.removeIf(field -> field.getEntityName().equals("updateTime"));
                }
                // fetch type actions
                Collection<UIInputEntity> actions = UIFieldUtils.fetchUIActionsFromClass(classType, entityContext);
                itemContexts.add(new ItemContext(classType.getSimpleName(), entityUIMetaData, actions));
            }

            return itemContexts;
        });
        return itemsBootstrapContextMap.get(key);
    }

    @GetMapping("/{type}/types")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Set<OptionModel> getImplementationsByBaseType(@PathVariable("type") String type) {
        // type nay be base class also
        Class<?> entityClassByType = entityManager.getClassByType(type);
        if (entityClassByType == null) {
            putTypeToEntityIfNotExists(type);
            if (!typeToEntityClassNames.containsKey(type)) {
                return Collections.emptySet();
            }
        }
        Set<OptionModel> list = entityClassByType == null ? new HashSet<>() : fetchCreateItemTypes(entityClassByType);
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            list.add(getUISideBarMenuOption(aClass));
        }
        return list;
    }

    @GetMapping("/type/{type}/options")
    public List<OptionModel> getItemOptionsByType(@PathVariable("type") String type) {
        putTypeToEntityIfNotExists(type);
        List<OptionModel> list = new ArrayList<>();
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            list.addAll(OptionModel.list(entityContext.findAll(aClass)));
        }
        Collections.sort(list);

        return list;
    }

    public void reloadItems(Collection<String> type) {
        entityContext.ui().sendNotification("-global", new JSONObject().put("type", "reloadItems")
                .put("value", type));
    }

    @PostMapping(value = "/{type}/installDep/{dependency}")
    public void installDep(@PathVariable("type") String type, @PathVariable("dependency") String dependency) {
        if (this.typeToRequireDependencies.containsKey(type)) {
            List<DependencyExecutableInstaller> installers = this.typeToRequireDependencies.get(type).installers;
            DependencyExecutableInstaller installer = installers.stream()
                    .filter(c -> c.getName().equals(dependency)).findAny().orElse(null);
            if (installer != null && installer.isRequireInstallDependencies(entityContext, false)) {
                entityContext.bgp().runWithProgress("install-deps-" + dependency, false,
                        progressBar -> {
                            installer.installDependency(entityContext, progressBar);
                            reloadItems(Collections.singletonList(type));
                        }, null,
                        () -> new RuntimeException("INSTALL_DEPENDENCY_IN_PROGRESS"));
            }
        }
    }

    @PostMapping(value = "/{entityID}/action")
    public ActionResponseModel callAction(@PathVariable("entityID") String entityID,
                                          @RequestBody ActionRequestModel requestModel) {
        return callActionWithBinary(entityID, requestModel, null);
    }

    @PostMapping(value = "/{entityID}/actionWithBinary")
    public ActionResponseModel callActionWithBinary(@PathVariable("entityID") String entityID,
                                                    @RequestPart ActionRequestModel actionRequestModel,
                                                    @RequestParam("data") MultipartFile[] files) {
        try {
            BaseEntity<?> entity = entityContext.getEntity(entityID);
            actionRequestModel.params.put("files", files);
            return executeAction(actionRequestModel, entity, entity);
        } catch (Exception ex) {
            return ActionResponseModel.showError(ex);
        }
    }

    @GetMapping("/{type}/actions")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public Collection<UIInputEntity> getItemsActions(@PathVariable("type") String type) {
        Class<?> entityClassByType = entityManager.getClassByType(type);
        return UIFieldUtils.fetchUIActionsFromClass(entityClassByType, entityContext);
    }

    @PostMapping("/{type}")
    public BaseEntity<?> create(@PathVariable("type") String type) {
        log.debug("Request creating entity by type: <{}>", type);
        Class<? extends BaseEntity> typeClass = EntityContextImpl.baseEntityNameToClass.get(type);
        if (typeClass == null) {
            throw new IllegalArgumentException("Unable to find base entity with type: " + type);
        }
        BaseEntity<?> baseEntity = CommonUtils.newInstance(typeClass);
        return entityContext.save(baseEntity);
    }

    @PostMapping("/{entityID}/copy")
    public BaseEntity<?> copyEntityByID(@PathVariable("entityID") String entityID) {
        BaseEntity<?> entity = entityContext.getEntity(entityID);
        entity.copy();
        return entityContext.save(entity);
    }

    @DeleteMapping("/{entityID}")
    @Secured(ADMIN_ROLE)
    public void removeEntity(@PathVariable("entityID") String entityID) {
        entityContext.delete(entityID);
    }

    @GetMapping("/{entityID}/dependencies")
    public List<String> canRemove(@PathVariable("entityID") String entityID) {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity == null) {
            entity = entityContext.getEntity(entityID, false);
            if (entity == null) {
                entityContext.getCacheService().clearCache();
                entityContext.ui().reloadWindow("Clear cache");
                return Collections.emptyList();
            }
        }
        AbstractRepository repository = entityContext.getRepository(entity).get();
        List<BaseEntity<?>> usages = getUsages(entityID, repository);
        return usages.stream().map(Object::toString).collect(Collectors.toList());
    }

    @PutMapping
    @SneakyThrows
    public BaseEntity<?> updateItems(@RequestBody String json) {
        JSONObject jsonObject = new JSONObject(json);
        BaseEntity<?> resultField = null;
        for (String entityId : jsonObject.keySet()) {
            log.info("Put update item: <{}>", entityId);
            BaseEntity<?> entity = entityContext.getEntity(entityId);

            if (entity == null) {
                throw new NotFoundException("Entity '" + entityId + "' not found");
            }

            JSONObject entityFields = jsonObject.getJSONObject(entityId);
            entity = objectMapper.readerForUpdating(entity).readValue(entityFields.toString());

            // reference fields isn't updatable, we need update them manually
            for (String fieldName : entityFields.keySet()) {
                Field field = FieldUtils.getField(entity.getClass(), fieldName, true);
                if (field != null && BaseEntity.class.isAssignableFrom(field.getType())) {
                    BaseEntity<?> refEntity = entityContext.getEntity(entityFields.getString(fieldName));
                    FieldUtils.writeField(field, entity, refEntity);
                }
            }

            // update entity
            BaseEntity<?> savedEntity = entityContext.save(entity);
            if (resultField == null) {
                resultField = savedEntity;
            }
        }
        return resultField;
    }

    @GetMapping("/typeContext/{type}")
    public ItemsByTypeResponse getItemsByTypeWithDependencies(@PathVariable("type") String type) {
        putTypeToEntityIfNotExists(type);
        List<BaseEntity> items = new ArrayList<>();
        List<ItemsByTypeResponse.TypeDependency> typeDependencies = new ArrayList<>();

        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            items.addAll(entityContext.findAll(aClass));
            TypeToRequireDependenciesContext dependenciesContext = typeToRequireDependencies.get(aClass.getSimpleName());
            if (dependenciesContext != null) {
                Set<String> notInstalledDependencies =
                        dependenciesContext.installers.stream().filter(i -> i.isEnabled(entityContext))
                                .map(DependencyExecutableInstaller::getName).collect(Collectors.toSet());
                typeDependencies.add(new ItemsByTypeResponse.TypeDependency(aClass.getSimpleName(), notInstalledDependencies));
            }
        }
        List<Collection<UIInputEntity>> contextActions = new ArrayList<>();
        for (BaseEntity item : items) {
            //TODO: Set<UIActionResponse> actions = Collections.emptySet();
            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            if (item instanceof HasDynamicContextMenuActions) {
                ((HasDynamicContextMenuActions) item).assembleActions(uiInputBuilder);
                // Set<? extends DynamicContextMenuAction> dynamicContextMenuActions = ((HasDynamicContextMenuActions) item)
                // .getActions(entityContext);
                /*if (dynamicContextMenuActions != null) {
                    actions = dynamicContextMenuActions.stream().map(UIActionResponse::new).collect(Collectors.toCollection
                    (LinkedHashSet::new));
                }*/
            }
            contextActions.add(uiInputBuilder.buildAll());
        }
        return new ItemsByTypeResponse(items, contextActions, typeDependencies);
    }

    @GetMapping("/service/{esName}")
    public List<EntityService> getItemsByEntityServiceType(@PathVariable("esName") String esName) {
        return entityContext.getEntityServices(EntityService.class).stream().filter(e -> {
            for (Class<?> anInterface : ClassUtils.getAllInterfaces(((EntityService<?, ?>) e).getEntityServiceItemClass())) {
                if (anInterface.getSimpleName().equals(esName)) {
                    return true;
                }
            }
            for (Class<?> anInterface : ClassUtils.getAllSuperclasses(((EntityService<?, ?>) e).getEntityServiceItemClass())) {
                if (anInterface.getSimpleName().equals(esName)) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList());
    }

    @GetMapping("/type/{type}")
    public List<BaseEntity> getItemsByType(@PathVariable("type") String type) {
        putTypeToEntityIfNotExists(type);
        List<BaseEntity> list = new ArrayList<>();
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            list.addAll(entityContext.findAll(aClass));
        }

        return list;
    }

    @PostMapping("/fireRouteAction")
    public ActionResponseModel fireRouteAction(@RequestBody RouteActionRequest routeActionRequest) {
        Class<? extends BaseEntity> aClass = baseEntitySimpleClasses.get(routeActionRequest.type);
        for (UISidebarButton button : aClass.getAnnotationsByType(UISidebarButton.class)) {
            if (button.handlerClass().getSimpleName().equals(routeActionRequest.handlerClass)) {
                return getOrCreateUIActionHandler(button.handlerClass()).handleAction(entityContext, null);
            }
        }
        return null;
    }

    @GetMapping("/{entityID}")
    public BaseEntity<?> getItem(@PathVariable("entityID") String entityID) {
        return entityManager.getEntityWithFetchLazy(entityID);
    }

    /*@PostMapping("/{entityID}/image")
    public DeviceBaseEntity updateItemImage(@PathVariable("entityID") String entityID, @RequestBody ImageEntity imageEntity) {
        return updateItem(entityID, true, baseEntity -> baseEntity.setImageEntity(imageEntity));
    }*/

    @SneakyThrows
    @PutMapping("/{entityID}/mappedBy/{mappedBy}")
    public BaseEntity<?> putToItem(@PathVariable("entityID") String entityID,
                                   @PathVariable("mappedBy") String mappedBy,
                                   @RequestBody String json) {
        JSONObject jsonObject = new JSONObject(json);
        BaseEntity<?> owner = entityContext.getEntity(entityID);

        for (String type : jsonObject.keySet()) {
            Class<? extends BaseEntity> className = entityManager.getClassByType(type);
            JSONObject entityFields = jsonObject.getJSONObject(type);
            BaseEntity<?> newEntity = objectMapper.readValue(entityFields.toString(), className);
            FieldUtils.writeField(newEntity, mappedBy, owner, true);
            entityContext.save(newEntity);
        }

        return entityContext.getEntity(owner);
    }

    @SneakyThrows
    @Secured(ADMIN_ROLE)
    @DeleteMapping("/{entityID}/field/{field}/item/{entityToRemove}")
    public BaseEntity<?> removeFromItem(@PathVariable("entityID") String entityID,
                                        @PathVariable("field") String field,
                                        @PathVariable("entityToRemove") String entityToRemove) {
        BaseEntity<?> entity = entityContext.getEntity(entityID);
        entityContext.delete(entityToRemove);
        return entityContext.getEntity(entity);
    }

    @PostMapping("/{entityID}/block")
    public void updateBlockPosition(@PathVariable("entityID") String entityID,
                                    @RequestBody UpdateBlockPosition position) {
        BaseEntity<?> entity = entityContext.getEntity(entityID);
        if (entity != null) {
            if (entity instanceof HasPosition) {
                HasPosition<?> hasPosition = (HasPosition<?>) entity;
                hasPosition.setXb(position.xb);
                hasPosition.setYb(position.yb);
                hasPosition.setBw(position.bw);
                hasPosition.setBh(position.bh);
                entityContext.save(entity);
            } else {
                throw new IllegalArgumentException("Entity: " + entityID + " has no ability to update position");
            }
        }
    }

    @PostMapping("/{entityID}/uploadImageBase64")
    @Secured(ADMIN_ROLE)
    public ImageEntity uploadImageBase64(@PathVariable("entityID") String entityID, @RequestBody BufferedImage bufferedImage) {
        try {
            return imageService.upload(entityID, bufferedImage);
        } catch (Exception e) {

            log.error(e.getMessage(), e);
            throw new ServerException(e);
        }
    }

    @Getter
    @Setter
    public static class GetOptionsRequest {
        private String fieldFetchType;
        private String selectType;
        private String param0; // for lazy loading
        private Map<String, String> deps;
    }

    @PostMapping("/{entityID}/{fieldName}/options")
    public Collection<OptionModel> loadSelectOptions(@PathVariable("entityID") String entityID,
                                                     @PathVariable("fieldName") String fieldName,
                                                     @RequestBody GetOptionsRequest optionsRequest) {
        Object classEntity = entityContext.getEntity(entityID);
        if (classEntity == null) {
            // i.e in case we load Widget
            Class<?> aClass = entityManager.getClassByType(entityID);
            if (aClass == null) {
                List<Class<?>> classImplementationsByType = findAllClassImplementationsByType(entityID);
                aClass = classImplementationsByType.isEmpty() ? null : classImplementationsByType.get(0);
            }
            classEntity = CommonUtils.newInstance(aClass);
            if (classEntity == null) {
                throw new IllegalArgumentException("Unable find class: " + entityID);
            }
        }
        Class<?> entityClass = classEntity.getClass();
        if (StringUtils.isNotEmpty(optionsRequest.getFieldFetchType())) {
            String[] bundleAndClassName = optionsRequest.getFieldFetchType().split(":");
            entityClass = entityContext.getBeanOfBundleBySimpleName(bundleAndClassName[0], bundleAndClassName[1]).getClass();
        }

        List<OptionModel> options = getEntityOptions(fieldName, classEntity, entityClass);
        if (options != null) {
            return options;
        }

        return UIFieldUtils.loadOptions(classEntity, entityContext, fieldName,
                null, optionsRequest.getSelectType(), optionsRequest.getDeps(), optionsRequest.getParam0());
    }

    @GetMapping("/{entityID}/{fieldName}/{selectedEntityID}/dynamicParameterOptions")
    public Collection<OptionModel> getDynamicParameterOptions(@PathVariable("entityID") String entityID,
                                                              @PathVariable("fieldName") String fieldName,
                                                              @PathVariable("selectedEntityID") String selectedEntityID) {
        Object classEntity = entityContext.getEntity(entityID);
        if (classEntity == null) {
            throw NotFoundException.entityNotFound(entityID);
        }
        Object selectedClassEntity = entityContext.getEntity(selectedEntityID);
        if (selectedClassEntity == null) {
            throw NotFoundException.entityNotFound(selectedEntityID);
        }

        if (!(selectedClassEntity instanceof SelectionWithDynamicParameterFields)) {
            throw new IllegalStateException(
                    "SelectedEntity must implement interface <" + SelectionWithDynamicParameterFields.class.getSimpleName() +
                            ">");
        }
        DynamicParameterFields dynamicParameterFields =
                ((SelectionWithDynamicParameterFields) selectedClassEntity).getDynamicParameterFields(classEntity);
        if (dynamicParameterFields == null) {
            throw new IllegalStateException("SelectedEntity getDynamicParameterFields returned null");
        }
        return UIFieldUtils.loadOptions(dynamicParameterFields, entityContext, fieldName, selectedClassEntity, null, null, null);

        /*if (classEntity == null) {
            // i.e in case we load Widget
            Class<?> aClass = entityManager.getClassByType(entityID);
            if (aClass == null) {
                List<Class<?>> classImplementationsByType = findAllClassImplementationsByType(entityID);
                aClass = classImplementationsByType.isEmpty() ? null : classImplementationsByType.get(0);
            }
            classEntity = CommonUtils.newInstance(aClass);
            if (classEntity == null) {
                throw new IllegalArgumentException("Unable find class: " + entityID);
            }
        }
        Class<?> entityClass = classEntity.getClass();
        if (StringUtils.isNotEmpty(fieldFetchType)) {
            String[] bundleAndClassName = fieldFetchType.split(":");
            entityClass = entityContext.getBeanOfBundleBySimpleName(bundleAndClassName[0], bundleAndClassName[1]).getClass();
        }

        List<OptionModel> options = getEntityOptions(fieldName, classEntity, entityClass);
        if (options != null) {
            return options;
        }

        return UIFieldUtils.loadOptions(classEntity, entityContext, fieldName);*/
    }

    private List<OptionModel> getEntityOptions(String fieldName, Object classEntity, Class<?> entityClass) {
        Field field = FieldUtils.getField(entityClass, fieldName, true);
        Class<?> returnType = field == null ? null : field.getType();
        if (returnType == null) {
            Method method = InternalUtil.findMethodByName(entityClass, fieldName);
            if (method == null) {
                return null;
            }
            returnType = method.getReturnType();
        }
        if (returnType.getDeclaredAnnotation(Entity.class) != null) {
            Class<BaseEntity<?>> clazz = (Class<BaseEntity<?>>) returnType;
            List<? extends BaseEntity> selectedOptions = entityContext.findAll(clazz);
            List<OptionModel> options =
                    selectedOptions.stream().map(t -> OptionModel.of(t.getEntityID(), t.getTitle())).collect(Collectors.toList());

            // make filtering/add messages/etc...
            Method filterOptionMethod = findFilterOptionMethod(fieldName, classEntity);
            if (filterOptionMethod != null) {
                try {
                    filterOptionMethod.setAccessible(true);
                    filterOptionMethod.invoke(classEntity, selectedOptions, options);
                } catch (Exception ignore) {
                }
            }
            return options;
        }
        return null;
    }

    private Set<OptionModel> fetchCreateItemTypes(Class<?> entityClassByType) {
        return classFinder.getClassesWithParent(entityClassByType)
                .stream()
                .map(this::getUISideBarMenuOption)
                .collect(Collectors.toSet());
    }

    private OptionModel getUISideBarMenuOption(Class<?> aClass) {
        OptionModel optionModel = OptionModel.key(aClass.getSimpleName());
        UISidebarChildren uiSidebarChildren = aClass.getAnnotation(UISidebarChildren.class);
        if (uiSidebarChildren != null) {
            optionModel.json(json -> json.put("icon", uiSidebarChildren.icon()).put("color", uiSidebarChildren.color()));
        }
        return optionModel;
    }

    // set synchronized to avoid calculate multiple times
    private synchronized void putTypeToEntityIfNotExists(String type) {
        if (!typeToEntityClassNames.containsKey(type)) {
            typeToEntityClassNames.put(type, new ArrayList<>());
            Class<? extends BaseEntity> baseEntityByName = baseEntitySimpleClasses.get(type);
            if (baseEntityByName != null) {
                if (Modifier.isAbstract(baseEntityByName.getModifiers())) {
                    typeToEntityClassNames.get(type).addAll(classFinder.getClassesWithParent(baseEntityByName).stream()
                            .filter((Predicate<Class>) child -> {
                                if (child.isAnnotationPresent(UISidebarChildren.class)) {
                                    UISidebarChildren uiSidebarChildren =
                                            (UISidebarChildren) child.getDeclaredAnnotation(UISidebarChildren.class);
                                    return uiSidebarChildren.allowCreateItem();
                                }
                                return true;
                            }).collect(Collectors.toList()));
                } else {
                    typeToEntityClassNames.get(type).add(baseEntityByName);
                }

                // populate if entity require extra packages to install
                Set<Class> baseClasses = new HashSet<>();
                for (Class<? extends BaseEntity> entityClass : typeToEntityClassNames.get(type)) {
                    baseClasses.addAll(ClassFinder.findAllParentClasses(entityClass, baseEntityByName));
                }

                computeRequireDependenciesForType(type, baseEntityByName);
            }
        }
    }

    private void computeRequireDependenciesForType(String type, Class<? extends BaseEntity> baseEntityByName) {
        // assemble all dependencies
        for (Class<? extends BaseEntity> entityClass : typeToEntityClassNames.get(type)) {
            List<DependencyExecutableInstaller> installers = new ArrayList<>();

            for (Class<?> baseClass : ClassFinder.findAllParentClasses(entityClass, baseEntityByName)) {
                for (RequireExecutableDependency dependency : baseClass.getAnnotationsByType(RequireExecutableDependency.class)) {
                    installers.add(getOrCreateUIActionHandler(dependency.installer()));
                }
            }
            typeToRequireDependencies.put(entityClass.getSimpleName(),
                    new TypeToRequireDependenciesContext(baseEntityByName, installers));
        }
    }

    private List<BaseEntity<?>> getUsages(String entityID, AbstractRepository<BaseEntity<?>> repository) {
        Object baseEntity = repository.getByEntityIDWithFetchLazy(entityID, false);
        List<BaseEntity<?>> usages = new ArrayList<>();
        if (baseEntity != null) {
            FieldUtils.getAllFieldsList(baseEntity.getClass()).forEach(field -> {
                try {
                    Class<? extends Annotation> aClass = field.isAnnotationPresent(OneToOne.class) ? OneToOne.class :
                            (field.isAnnotationPresent(OneToMany.class) ? OneToMany.class : null);
                    if (aClass != null) {
                        Object targetValue = FieldUtils.readField(field, baseEntity, true);
                        if (targetValue instanceof Collection) {
                            if (!((Collection<?>) targetValue).isEmpty()) {
                                for (Object o : (Collection<?>) targetValue) {
                                    o.toString(); // hibernate initialize
                                    usages.add((BaseEntity<?>) o);
                                }
                            }
                        } else if (targetValue != null) {
                            usages.add((BaseEntity<?>) targetValue);
                        }
                    }
                } catch (Exception e) {
                    throw new ServerException(e);
                }
            });
        }
        return usages;
    }

    private Method findFilterOptionMethod(String fieldName, Object entity) {
        for (Method declaredMethod : entity.getClass().getDeclaredMethods()) {
            if (declaredMethod.isAnnotationPresent(UIFilterOptions.class) &&
                    declaredMethod.getAnnotation(UIFilterOptions.class).value().equals(fieldName)) {
                return declaredMethod;
            }
        }

        // if Class has only one selection and only one filtered method - use it
        long count = FieldUtils.getFieldsListWithAnnotation(entity.getClass(), UIField.class).stream()
                .map(p -> p.getAnnotation(UIField.class).type())
                .filter(f -> f == UIFieldType.SelectBox).count();
        if (count == 1) {
            List<Method> methodsListWithAnnotation = Stream.of(entity.getClass().getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(UIFilterOptions.class))
                    .collect(Collectors.toList());

            if (methodsListWithAnnotation.size() == 1) {
                return methodsListWithAnnotation.get(0);
            }
        }
        return null;
    }

    private List<Class<?>> findAllClassImplementationsByType(String type) {
        Class<?> classByType = entityManager.getClassByType(type);
        List<Class<?>> classTypes = new ArrayList<>();
        if (classByType == null) {
            putTypeToEntityIfNotExists(type);
            if (!typeToEntityClassNames.containsKey(type)) {
                return classTypes;
            }
            classTypes.addAll(typeToEntityClassNames.getOrDefault(type, Collections.emptyList()));
        } else {
            classTypes.add(classByType);
        }
        if (classTypes.isEmpty()) {
            classTypes.addAll(classFinder.getClassesWithParent(DynamicParameterFields.class));
        }
        return classTypes;
    }

    public void reloadItemsRelatedToDependency(DependencyExecutableInstaller installer) {
        Set<String> typesToReload = new HashSet<>();
        Set<String> headerButtonsUpdateStatus = new HashSet<>();
        for (Map.Entry<String, TypeToRequireDependenciesContext> entry : typeToRequireDependencies.entrySet()) {
            List<DependencyExecutableInstaller> installers = entry.getValue().installers;
            if (installers.stream().anyMatch(i -> i.getName().equals(installer.getName()))) {
                typesToReload.add(entry.getKey());

                // some BaseEntity has @UISidebarButton with ability to install dependency from header UI. So we should reflect
                // them also
                for (Pair<Class, List<UISidebarButton>> classListEntry : ClassFinder.findAllAnnotationsToParentAnnotation(
                        entry.getValue().typeClass, UISidebarButton.class, UISidebarMenu.class)) {
                    for (UISidebarButton uiSidebarButton : classListEntry.getSecond()) {
                        // find only header buttons that belong to dependency installer
                        if (installer.getClass().isAssignableFrom(uiSidebarButton.handlerClass())) {
                            headerButtonsUpdateStatus.add(classListEntry.getFirst().getSimpleName() + "_" +
                                    uiSidebarButton.handlerClass().getSimpleName());
                        }
                    }
                }
            }
        }
        // fire reload items
        reloadItems(typesToReload);
        // send to ui to reflect header dependencies
        boolean requireInstallDependencies = installer.isRequireInstallDependencies(entityContext, true);
        for (String entityID : headerButtonsUpdateStatus) {
            entityContext.ui().disableHeaderButton(entityID, !requireInstallDependencies);
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static class ItemsByTypeResponse {
        private final List<BaseEntity> items;
        private final List<Collection<UIInputEntity>> contextActions;
        private final List<TypeDependency> typeDependencies;

        @Getter
        @AllArgsConstructor
        private static class TypeDependency {
            private String name;
            private Set<String> dependencies;
        }
    }

    @Data
    private static class UpdateBlockPosition {
        private int xb;
        private int yb;
        private int bw;
        private int bh;
    }

    @Setter
    private static class RouteActionRequest {
        private String type;
        private String handlerClass;
    }

    @Getter
    @Setter
    public static class ActionRequestModel {
        private String name;
        private JSONObject metadata;
        private JSONObject params;
    }

    @Getter
    @AllArgsConstructor
    private static class ItemContext {
        private final String type;
        private final List<EntityUIMetaData> fields;
        private final Collection<UIInputEntity> actions;
    }
}
