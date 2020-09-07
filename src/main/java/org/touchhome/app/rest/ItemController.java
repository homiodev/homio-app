package org.touchhome.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.json.UIActionDescription;
import org.touchhome.app.manager.ImageManager;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.manager.common.EntityManager;
import org.touchhome.app.manager.common.InternalManager;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.bundle.api.DynamicOptionLoader;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.model.HasPosition;
import org.touchhome.bundle.api.model.ImageEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldSelection;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIFilterOptions;
import org.touchhome.bundle.api.ui.method.UIFieldSelectValueOnEmpty;
import org.touchhome.bundle.api.ui.method.UIMethodAction;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.awt.image.BufferedImage;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.touchhome.app.rest.UtilsController.fillEntityUIMetadataList;

@Log4j2
@RestController
@RequestMapping("/rest/item")
@RequiredArgsConstructor
public class ItemController {

    private static Map<String, List<Class<? extends BaseEntity>>> typeToEntityClassNames = new HashMap<>();
    private final Map<String, List<EntityUIMetaData>> fieldsMap = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final InternalManager entityContext;
    private final EntityManager entityManager;
    private final ClassFinder classFinder;
    private final ImageManager imageManager;
    private final ApplicationContext applicationContext;

    private Map<String, Class<? extends BaseEntity>> baseEntitySimpleClasses;

    @SneakyThrows
    static Object executeAction(UIActionDescription uiActionDescription, Object actionHolder, ApplicationContext applicationContext, BaseEntity actionEntity) {
        if (UIActionDescription.Type.method.equals(uiActionDescription.getType())) {
            for (Method method : MethodUtils.getMethodsWithAnnotation(actionHolder.getClass(), UIMethodAction.class)) {
                UIMethodAction uiMethodAction = method.getDeclaredAnnotation(UIMethodAction.class);
                if (uiMethodAction.name().equals(uiActionDescription.getName())) {
                    return executeMethodAction(method, actionHolder, applicationContext, actionEntity);
                }
            }
            throw new IllegalArgumentException("Execution method name: <" + uiActionDescription.getName() + "> not implemented");
        }
        throw new IllegalArgumentException("Execution method type: <" + uiActionDescription.getType() + "> not implemented");
    }

    @SneakyThrows
    static Object executeMethodAction(Method method, Object actionHolder, ApplicationContext applicationContext, BaseEntity actionEntity) {
        List<Object> objects = new ArrayList<>();
        for (AnnotatedType parameterType : method.getAnnotatedParameterTypes()) {
            if (BaseEntity.class.isAssignableFrom((Class) parameterType.getType())) {
                objects.add(actionEntity);
            } else {
                objects.add(applicationContext.getBean((Class) parameterType.getType()));
            }
        }
        method.setAccessible(true);
        return method.invoke(actionHolder, objects.toArray());
    }

    static List<UIActionDescription> fetchUIHeaderActions(Map<String, Class<? extends BundleSettingPlugin>> actionMap) {
        List<UIActionDescription> actions = new ArrayList<>();
        if (actionMap != null) {
            for (Map.Entry<String, Class<? extends BundleSettingPlugin>> entry : actionMap.entrySet()) {
                actions.add(new UIActionDescription().setType(UIActionDescription.Type.header).setName(entry.getKey())
                        .setMetadata(new JSONObject().put("ref", SettingEntity.PREFIX + entry.getValue().getSimpleName())));
            }
        }
        return actions;
    }

    static List<UIActionDescription> fetchUIActionsFromClass(Class<?> clazz) {
        List<UIActionDescription> actions = new ArrayList<>();
        if (clazz != null) {
            for (Method method : MethodUtils.getMethodsWithAnnotation(clazz, UIMethodAction.class)) {
                UIMethodAction uiMethodAction = method.getDeclaredAnnotation(UIMethodAction.class);
                actions.add(new UIActionDescription().setType(UIActionDescription.Type.method).setName(uiMethodAction.name()).setResponseAction(uiMethodAction.responseAction().name()));
            }
        }
        return actions;
    }

    public void postConstruct() {
        this.baseEntitySimpleClasses = classFinder.getClassesWithParent(BaseEntity.class, null, null)
                .stream().collect(Collectors.toMap(Class::getSimpleName, s -> s));
        this.baseEntitySimpleClasses.put(DeviceBaseEntity.class.getSimpleName(), DeviceBaseEntity.class);
    }

    @GetMapping("{type}/fields")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<EntityUIMetaData> getUIFieldsByType(@PathVariable("type") String type,
                                                    @RequestParam(value = "subType", defaultValue = "") String subType) {
        String key = type + subType;
        fieldsMap.computeIfAbsent(key, s -> {
            Class<?> entityClassByType = entityManager.getClassByType(type);
            List<EntityUIMetaData> entityUIMetaData = fillEntityUIMetadataList(entityClassByType, new HashSet<>());

            if (subType.length() > 0 && subType.contains(":")) {
                String[] bundleAndClassName = subType.split(":");
                Object subClassObject = entityContext.getBeanOfBundleBySimpleName(bundleAndClassName[0], bundleAndClassName[1]);
                List<EntityUIMetaData> subTypeFieldMetadata = fillEntityUIMetadataList(subClassObject, new HashSet<>());
                // add 'cutFromJson' because custom fields must be fetched from json parameter (uses first available json parameter)
                for (EntityUIMetaData subTypeFieldMetadatum : subTypeFieldMetadata) {
                    subTypeFieldMetadatum.setTypeMetaData(new JSONObject(StringUtils.defaultString(subTypeFieldMetadatum.getTypeMetaData(), "{}")).put("cutFromJson", true).toString());
                }
                entityUIMetaData.addAll(subTypeFieldMetadata);
            }

            return entityUIMetaData;
        });
        return fieldsMap.get(key);
    }

    @GetMapping("/{type}/extended")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<Option> getExtendedItemTypes(@PathVariable("type") String type) {
        Class<?> entityClassByType = entityManager.getClassByType(type);
        return classFinder.getClassesWithParent(entityClassByType)
                .stream()
                .map(aClass -> {
                    UISidebarMenu uiSidebarMenu = aClass.getAnnotation(UISidebarMenu.class);
                    return uiSidebarMenu == null ? Option.key(aClass.getSimpleName()) :
                            Option.key(aClass.getSimpleName());
                })
                .collect(Collectors.toList());
    }

    @PostMapping(value = "{entityID}/action")
    public Object executeAction(@PathVariable("entityID") String entityID, @RequestBody UIActionDescription uiActionDescription) {
        BaseEntity entity = entityContext.getEntity(entityID);
        return ItemController.executeAction(uiActionDescription, entity, applicationContext, entity);
    }

    @GetMapping("{type}/actions")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<UIActionDescription> getItemsActions(@PathVariable("type") String type) {
        Class<?> entityClassByType = entityManager.getClassByType(type);
        return fetchUIActionsFromClass(entityClassByType);
    }

    @PostMapping("{type}")
    public BaseEntity create(@PathVariable("type") String type) throws Exception {
        log.debug("Request creating entity by type: <{}>", type);
        AbstractRepository<BaseEntity> entityRepositoryByType = entityContext.getRepositoryByClass(type);
        BaseEntity baseEntity = entityRepositoryByType.getEntityClass().getConstructor().newInstance();
        return entityContext.save(baseEntity);
    }

    @PostMapping("{entityID}/copy")
    public BaseEntity copyEntityByID(@PathVariable("entityID") String entityID) {
        BaseEntity entity = entityContext.getEntity(entityID);
        entity.copy();
        return entityContext.save(entity);
    }

    @DeleteMapping("{entityID}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void removeEntity(@PathVariable("entityID") String entityID) {
        entityContext.delete(entityID);
    }

    @GetMapping("{entityID}/dependencies")
    public List<String> canRemove(@PathVariable("entityID") String entityID) {
        AbstractRepository repository = entityContext.getRepository(entityContext.getEntity(entityID)).orElse(null);
        List<BaseEntity> usages = getUsages(entityID, repository);
        return usages.stream().map(Object::toString).collect(Collectors.toList());
    }

    @PutMapping
    @SneakyThrows
    public BaseEntity updateItems(@RequestBody String json) {
        JSONObject jsonObject = new JSONObject(json);
        BaseEntity resultField = null;
        for (String entityId : jsonObject.keySet()) {
            BaseEntity entity = entityContext.getEntity(entityId);

            if (entity == null) {
                throw new NotFoundException("Entity '" + entityId + "' not found");
            }

            JSONObject entityFields = jsonObject.getJSONObject(entityId);
            entity = objectMapper.readerForUpdating(entity).readValue(entityFields.toString());

            // reference fields isn't updatable, we need update them manually
            for (String fieldName : entityFields.keySet()) {
                Field field = FieldUtils.getField(entity.getClass(), fieldName, true);
                if (field != null && BaseEntity.class.isAssignableFrom(field.getType())) {
                    BaseEntity refEntity = entityContext.getEntity(entityFields.getString(fieldName));
                    FieldUtils.writeField(field, entity, refEntity);
                }
            }

            // update entity
            BaseEntity savedEntity = entityContext.save(entity);
            if (resultField == null) {
                resultField = savedEntity;
            }
        }
        return resultField;
    }

    @GetMapping("type/{type}")
    public List<BaseEntity> getItemsByType(@PathVariable("type") String type) {
        putTypeToEntityIfNotExists(type);
        List<BaseEntity> list = new ArrayList<>();
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            list.addAll(entityContext.findAll(aClass));
        }

        return list;
    }

    @GetMapping("type/{type}/options")
    public List<Option> getItemOptionsByType(@PathVariable("type") String type) {
        putTypeToEntityIfNotExists(type);
        List<Option> list = new ArrayList<>();
        for (Class<? extends BaseEntity> aClass : typeToEntityClassNames.get(type)) {
            list.addAll(Option.list(entityContext.findAll(aClass)));
        }
        Collections.sort(list);

        return list;
    }

    private void putTypeToEntityIfNotExists(String type) {
        if (!typeToEntityClassNames.containsKey(type)) {
            typeToEntityClassNames.put(type, new ArrayList<>());
            Class<? extends BaseEntity> baseEntityByName = baseEntitySimpleClasses.get(type);
            if (baseEntityByName != null) {
                if (Modifier.isAbstract(baseEntityByName.getModifiers())) {
                    typeToEntityClassNames.get(type).addAll(classFinder.getClassesWithParent(baseEntityByName));
                } else {
                    typeToEntityClassNames.get(type).add(baseEntityByName);
                }
            }
        }
    }

    @GetMapping("{entityID}")
    public BaseEntity getItem(@PathVariable("entityID") String entityID) {
        return entityManager.getEntityWithFetchLazy(entityID);
    }

    @SneakyThrows
    @PutMapping("{entityID}/mappedBy/{mappedBy}")
    public BaseEntity putToItem(@PathVariable("entityID") String entityID,
                                @PathVariable("mappedBy") String mappedBy,
                                @RequestBody String json) {
        JSONObject jsonObject = new JSONObject(json);
        BaseEntity owner = entityContext.getEntity(entityID);

        for (String type : jsonObject.keySet()) {
            Class<? extends BaseEntity> className = entityManager.getClassByType(type);
            JSONObject entityFields = jsonObject.getJSONObject(type);
            BaseEntity newEntity = objectMapper.readValue(entityFields.toString(), className);
            FieldUtils.writeDeclaredField(newEntity, mappedBy, owner, true);
            entityContext.save(newEntity);
        }

        return entityContext.getEntity(owner);
    }

    /*@PostMapping("{entityID}/image")
    public DeviceBaseEntity updateItemImage(@PathVariable("entityID") String entityID, @RequestBody ImageEntity imageEntity) {
        return updateItem(entityID, true, baseEntity -> baseEntity.setImageEntity(imageEntity));
    }*/

    @SneakyThrows
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    @DeleteMapping("{entityID}/field/{field}/item/{entityToRemove}")
    public BaseEntity removeFromItem(@PathVariable("entityID") String entityID,
                                     @PathVariable("field") String field,
                                     @PathVariable("entityToRemove") String entityToRemove) {
        BaseEntity entity = entityContext.getEntity(entityID);
        entityContext.delete(entityToRemove);
        return entityContext.getEntity(entity);
    }

    @PostMapping("{entityID}/block")
    public void updateBlockPosition(@PathVariable("entityID") String entityID,
                                    @RequestBody UpdateBlockPosition position) {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity != null) {
            if (entity instanceof HasPosition) {
                HasPosition hasPosition = (HasPosition) entity;
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

    @PostMapping("{entityID}/uploadImageBase64")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public ImageEntity uploadImageBase64(@PathVariable("entityID") String entityID, @RequestBody BufferedImage bufferedImage) {
        try {
            return imageManager.upload(entityID, bufferedImage);
        } catch (Exception e) {

            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping("{entityID}/{fieldName}/options")
    public List loadSelectOptions(@PathVariable("entityID") String entityID,
                                  @PathVariable("fieldName") String fieldName,
                                  @RequestParam("fieldFetchType") String fieldFetchType) throws Exception {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity == null) {
            entity = getInstanceByClass(entityID); // i.e in case we load Widget
        }
        Class entityClass = entity.getClass();
        if (StringUtils.isNotEmpty(fieldFetchType)) {
            String[] bundleAndClassName = fieldFetchType.split(":");
            entityClass = entityContext.getBeanOfBundleBySimpleName(bundleAndClassName[0], bundleAndClassName[1]).getClass();
        }

        Field field = FieldUtils.getField(entityClass, fieldName, true);
        if (field.getType().getDeclaredAnnotation(Entity.class) != null) {
            Class<BaseEntity> clazz = (Class<BaseEntity>) field.getType();
            List<? extends BaseEntity> selectedOptions = entityContext.findAll(clazz);
            List<Option> options = selectedOptions.stream().map(t -> new Option(t.getEntityID(), t.getTitle())).collect(Collectors.toList());

            // make filtering/add messages/etc...
            Method filterOptionMethod = findFilterOptionMethod(fieldName, entity);
            if (filterOptionMethod != null) {
                try {
                    filterOptionMethod.setAccessible(true);
                    filterOptionMethod.invoke(entity, selectedOptions, options);
                } catch (Exception ignore) {
                }
            }
            return options;
        }
        return loadOptions(entity, field, entityContext);
    }

    @SneakyThrows
    public static List<Option> loadOptions(Object entity, Field field, EntityContext entityContext) {
        Class targetClass = field.getType();
        UIFieldSelection uiFieldTargetSelection = field.getDeclaredAnnotation(UIFieldSelection.class);
        if (uiFieldTargetSelection != null) {
            targetClass = uiFieldTargetSelection.optionLoader();
        }

        UIFieldSelectValueOnEmpty uiFieldSelectValueOnEmpty = field.getDeclaredAnnotation(UIFieldSelectValueOnEmpty.class);
        if (uiFieldSelectValueOnEmpty != null) {
            targetClass = uiFieldSelectValueOnEmpty.optionLoader();
        }

        if (DynamicOptionLoader.class.isAssignableFrom(targetClass)) {
            DynamicOptionLoader dynamicOptionLoader = (DynamicOptionLoader) targetClass.newInstance();
            return dynamicOptionLoader.loadOptions(null, entityContext);
        }

        if (DynamicOptionLoader.class.isAssignableFrom(field.getType())) {
            DynamicOptionLoader dynamicOptionLoader = (DynamicOptionLoader) field.get(entity);
            return dynamicOptionLoader.loadOptions(null, entityContext);
        }

        if (targetClass.isEnum()) {
            return Stream.of(targetClass.getEnumConstants()).map(e -> Option.key(e.toString())).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private List<BaseEntity> getUsages(String entityID, AbstractRepository<BaseEntity> repository) {
        Object baseEntity = repository.getByEntityIDWithFetchLazy(entityID, true);
        List<BaseEntity> usages = new ArrayList<>();
        if (baseEntity != null) {
            FieldUtils.getAllFieldsList(baseEntity.getClass()).forEach(field -> {
                try {
                    Class<? extends Annotation> aClass = field.isAnnotationPresent(OneToOne.class) ? OneToOne.class :
                            (field.isAnnotationPresent(OneToMany.class) ? OneToMany.class : null);
                    if (aClass != null/* && !field.isAnnotationPresent(UIDeletableCascade.class)*/) {
                        Object targetValue = FieldUtils.readField(field, baseEntity, true);
                        if (targetValue instanceof Collection) {
                            if (!((Collection) targetValue).isEmpty()) {
                                for (Object o : (Collection) targetValue) {
                                    o.toString(); // hibernate initialize
                                    usages.add((BaseEntity) o);
                                }
                            }
                        } else if (targetValue != null) {
                            usages.add((BaseEntity) targetValue);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return usages;
    }

    private Method findFilterOptionMethod(String fieldName, Object entity) {
        for (Method declaredMethod : entity.getClass().getDeclaredMethods()) {
            if (declaredMethod.isAnnotationPresent(UIFilterOptions.class) && declaredMethod.getAnnotation(UIFilterOptions.class).value().equals(fieldName)) {
                return declaredMethod;
            }
        }

        // if Class has only one selection and only one filtered method - use it
        long count = FieldUtils.getFieldsListWithAnnotation(entity.getClass(), UIField.class).stream().map(p -> p.getAnnotation(UIField.class).type())
                .filter(f -> f == UIFieldType.Selection).count();
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

    private BaseEntity getInstanceByClass(String className) throws Exception {
        Class<?> aClass = entityManager.getClassByType(className);
        for (Constructor<?> constructor : aClass.getConstructors()) {
            if (constructor.getParameterCount() == 0) {
                constructor.setAccessible(true);
                return (BaseEntity) constructor.newInstance();
            }
        }
        throw new IllegalArgumentException("Unable find class: " + className);
    }

    @Data
    private static class UpdateBlockPosition {
        private int xb;
        private int yb;
        private int bw;
        private int bh;
    }
}
