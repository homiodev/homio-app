package org.touchhome.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.json.UIActionDescription;
import org.touchhome.app.manager.ImageManager;
import org.touchhome.app.manager.common.EntityManager;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.model.rest.EntityUIMetaData;
import org.touchhome.bundle.api.DynamicOptionLoader;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.ImageEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.console.UIHeaderSettingAction;
import org.touchhome.bundle.api.ui.console.UIHeaderSettingActions;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldTargetSelection;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIFilterOptions;
import org.touchhome.bundle.api.ui.method.UIMethodAction;
import org.touchhome.bundle.api.util.ClassFinder;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.raspberry.RaspberryManager;

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
@AllArgsConstructor
public class ItemController {

    private static Map<String, List<Class<? extends BaseEntity>>> typeToEntityClassNames = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final RaspberryManager raspberryManager;
    private final EntityContext entityContext;
    private final EntityManager entityManager;
    private final ClassFinder classFinder;
    private final ImageManager imageManager;
    private final ApplicationContext applicationContext;
    private final Map<String, Class<? extends BaseEntity>> baseEntitySimpleClasses;

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

    static List<UIActionDescription> fetchUIHeaderActions(Class<?> clazz) {
        List<UIActionDescription> actions = new ArrayList<>();
        if (clazz != null) {
            if (clazz.isAnnotationPresent(UIHeaderSettingActions.class)) {
                UIHeaderSettingActions uiHeaderSettingActions = clazz.getDeclaredAnnotation(UIHeaderSettingActions.class);
                for (UIHeaderSettingAction action : uiHeaderSettingActions.value()) {
                    actions.add(new UIActionDescription().setType(UIActionDescription.Type.header).setName(action.name())
                            .setMetadata(new JSONObject().put("ref", SettingEntity.PREFIX + action.setting().getSimpleName())));
                }
            }
            if (clazz.isAnnotationPresent(UIHeaderSettingAction.class)) {
                for (UIHeaderSettingAction action : clazz.getDeclaredAnnotationsByType(UIHeaderSettingAction.class)) {
                    actions.add(new UIActionDescription().setType(UIActionDescription.Type.header).setName(action.name())
                            .setMetadata(new JSONObject().put("ref", SettingEntity.PREFIX + action.setting().getSimpleName())));
                }
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

    @GetMapping("{type}/fields")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<EntityUIMetaData> getUIFieldsByType(@PathVariable("type") String type) {
        Class<?> entityClassByType = entityManager.getClassByType(type);
        return fillEntityUIMetadataList(entityClassByType, new HashSet<>());
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
                if (BaseEntity.class.isAssignableFrom(field.getType())) {
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
            list.addAll(entityContext.findAll(aClass).stream().map(e -> Option.of(e.getEntityID(), e.getTitle())).collect(Collectors.toList()));
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

    /*@GetMapping("{entityID}/image")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ImageEntity loadImageEntity(@PathVariable("entityID") String entityID) {
        BaseEntity entity = entityManager.getEntityWithFetchLazy(entityID);
        if (entity instanceof DeviceBaseEntity) {
            return ((DeviceBaseEntity) entity).getImageEntity();
        }
        throw new IllegalStateException("Unable find image for entity: " + entityID);
    }*/

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
            entity.setXb(position.xb);
            entity.setYb(position.yb);
            entity.setBw(position.bw);
            entity.setBh(position.bh);
            entityContext.save(entity);
        }
    }

    @GetMapping("raspberry/DS18B20")
    public List<BaseEntity> getRaspberryDS18B20() {
        return raspberryManager.getDS18B20()
                .stream().map(s -> BaseEntity.of(s, s)).collect(Collectors.toList());
    }

    @PostMapping("{entityID}/uploadImageBase64")
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
                                  @RequestParam("selectOptionMethod") String selectOptionMethod) throws Exception {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity == null) {
            entity = getInstanceByClass(entityID); // i.e in case we load Widget
        }
        if (StringUtils.isNotEmpty(selectOptionMethod)) {
            Method method = TouchHomeUtils.findRequreMethod(entity.getClass(), selectOptionMethod);
            return (List) executeMethodAction(method, entity, applicationContext, entity);
        }

        Field field = FieldUtils.getField(entity.getClass(), fieldName, true);

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
        Class targetClass = field.getType();
        UIFieldTargetSelection uiFieldTargetSelection = field.getDeclaredAnnotation(UIFieldTargetSelection.class);
        if (uiFieldTargetSelection != null) {
            targetClass = uiFieldTargetSelection.target();
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
        private Integer xb;
        private Integer yb;
        private Integer bw;
        private Integer bh;
    }
}
