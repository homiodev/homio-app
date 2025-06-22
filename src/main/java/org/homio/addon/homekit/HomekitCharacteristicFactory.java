package org.homio.addon.homekit;

import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.CharacteristicEnum;
import io.github.hapjava.characteristics.ExceptionalConsumer;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.base.*;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.homekit.accessories.Characteristics;
import org.homio.addon.homekit.annotations.HomekitCharacteristic;
import org.homio.addon.homekit.annotations.HomekitValidValues;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.homio.api.util.CommonUtils.findObjectConstructor;
import static org.homio.api.util.CommonUtils.getMethodTrimName;

@Log4j2
public class HomekitCharacteristicFactory {

    @SneakyThrows
    public static void buildCharacteristics(@NotNull HomekitEndpointContext ctx,
                                            @NotNull Characteristics characteristics) {
        String accessoryType = ctx.endpoint().getAccessoryType().name();
        for (Method method : HomekitEndpointEntity.class.getDeclaredMethods()) {
            var hc = findCorrectHomekitCharacteristic(method, ctx.endpoint().getAccessoryType().name());
            if (hc == null) {
                continue;
            }
            var fieldGroup = method.getAnnotation(UIFieldGroup.class);
            boolean required = fieldGroup.value().equals("REQ_CHAR");
            boolean optional = fieldGroup.value().equals("OPT_CHAR");
            if (!required && !optional) {
                continue;
            }
            var condition = method.getAnnotation(UIFieldShowOnCondition.class);
            if (!condition.value().contains(accessoryType)) {
                continue;
            }
            String varValue = null;
            try {
                varValue = (String) method.invoke(ctx.endpoint());
            } catch (Exception ex) {
                log.error("[{}]: Unable to invoke method: {}", ctx.owner().getEntityID(), method.getName());
            }
            if (StringUtils.isEmpty(varValue)) {
                continue;
            }
            if (characteristics.has(hc.type())) {
                continue;
            }
            var characteristicClass = hc.value();
            ContextVar.Variable v = ctx.getVariable(varValue);
            if (v == null) {
                if (required) {
                    throw new RuntimeException("Missing required variable: '" + getMethodTrimName(method) + "' for accessory: " + accessoryType);
                }
                continue;
            }
            if (!hc.impl().equals(HomekitCharacteristic.DefaultSupplier.class)) {
                characteristics.addIfNotNull(hc.type(), createCustomCharacteristic(ctx, hc, v));
            } else if (BooleanCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createBooleanCharacteristic(ctx, hc, v));
            } else if (IntegerCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createIntegerCharacteristic(ctx, hc, v));
            } else if (EnumCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createEnumCharacteristic(ctx, hc, v, method));
            } else if (FloatCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createFloatCharacteristic(ctx, hc, v));
            } else if (StringCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createStringCharacteristic(ctx, hc, v));
            } else {
                throw new RuntimeException("Unable to find handler for characteristic: " + characteristicClass);
            }
        }
    }

    private static HomekitCharacteristic findCorrectHomekitCharacteristic(Method method, String accessoryName) {
        var annotations = method.getAnnotationsByType(HomekitCharacteristic.class);
        if (annotations.length == 0) {
            return null;
        } else if (annotations.length == 1) {
            return annotations[0];
        }
        for (HomekitCharacteristic hc : annotations) {
            boolean negative = hc.forAccessory().startsWith("!");
            if (negative) {
                if (!hc.forAccessory().substring(1).equals(accessoryName)) {
                    return hc;
                }
            } else if (hc.forAccessory().equals(accessoryName)) {
                return hc;
            }
        }
        return null;
    }

    private static Characteristic createEnumCharacteristic(@NotNull HomekitEndpointContext c,
                                                           @NotNull HomekitCharacteristic hc,
                                                           @NotNull ContextVar.Variable v,
                                                           @NotNull Method method) {
        Type superType = hc.value().getGenericSuperclass();
        if (superType instanceof ParameterizedType) {
            Type enumType = ((ParameterizedType) superType).getActualTypeArguments()[0];

            if (enumType instanceof Class && ((Class<?>) enumType).isEnum()) {
                Set<? extends Enum> validValues = null;
                for (HomekitValidValues vv : method.getAnnotationsByType(HomekitValidValues.class)) {
                    if (vv.value().equals(enumType)) {
                        validValues = findEndpointValidValues(vv.value(), c.endpoint());
                    }
                }
                Class enumClass = (Class<?>) enumType;
                Map<Enum, Object> m = createMapping(v, enumClass);
                String defValue = hc.defaultStringValue();
                Object defaultEnum = StringUtils.isEmpty(defValue)
                        ? enumClass.getEnumConstants()[0]
                        : Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), defValue);

                List<Object> parameters = new ArrayList<>();
                if (validValues != null) {
                    Object[] allValidValues = (Object[]) Array.newInstance(enumClass, validValues.size());
                    System.arraycopy(validValues.toArray(), 0, allValidValues, 0, validValues.size());
                    parameters.add(allValidValues);
                }
                parameters.add((Supplier<CompletableFuture<?>>) () -> completedFuture(getKeyFromMapping2(v, m, defaultEnum)));
                parameters.add((ExceptionalConsumer<?>) newVal -> {
                    setHomioVariableFromEnum2(v, newVal, m);
                    // setIntConsumer(v, c),
                });
                parameters.add(getSubscriber(v, c, hc.type()));
                parameters.add(getUnsubscriber(v, c, hc.type()));

                BaseCharacteristic<?> characteristic = newCharacteristicInstance(hc.value(), parameters.toArray());

                c.setCharacteristic(characteristic, v, hc.type());
                return characteristic;
            }
        }
        return null;
    }

    @SneakyThrows
    private static Set<? extends Enum> findEndpointValidValues(
            @NotNull Class<? extends Enum> enumClass,
            @NotNull HomekitEndpointEntity endpoint) {
        //
        for (Method method : HomekitEndpointEntity.class.getMethods()) {
            if (Set.class.isAssignableFrom(method.getReturnType())) {
                Type returnType = method.getGenericReturnType();
                if (returnType instanceof ParameterizedType pt) {
                    Type actualType = pt.getActualTypeArguments()[0];
                    if (actualType instanceof Class && enumClass.isAssignableFrom((Class<?>) actualType)) {
                        return (Set<? extends Enum>) method.invoke(endpoint);
                    }
                }
            }
        }
        throw new RuntimeException("Unable to find related method for valid values: " + enumClass);
    }

    private static Characteristic createStringCharacteristic(
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristic hc,
            @NotNull ContextVar.Variable v) {
        //
        var characteristic = newCharacteristicInstance(hc.value(),
                getStringSupplier(v, hc.defaultStringValue()),
                setStringConsumer(v),
                getSubscriber(v, c, hc.type()),
                getUnsubscriber(v, c, hc.type()));
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    private static Characteristic createFloatCharacteristic(
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristic hc,
            @NotNull ContextVar.Variable v) {
        //
        var characteristic = newCharacteristicInstance(hc.value(),
                getDoubleSupplier(v, hc.defaultDoubleValue()),
                setDoubleConsumer(v),
                getSubscriber(v, c, hc.type()),
                getUnsubscriber(v, c, hc.type()));
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    private static Characteristic createIntegerCharacteristic(
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristic hc,
            @NotNull ContextVar.Variable v) {
        //
        var characteristic = newCharacteristicInstance(hc.value(),
                getIntSupplier(v, hc.defaultIntValue()),
                setIntConsumer(v),
                getSubscriber(v, c, hc.type()),
                getUnsubscriber(v, c, hc.type()));
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    @SneakyThrows
    private static BaseCharacteristic newCharacteristicInstance(
            @NotNull Class<? extends BaseCharacteristic> clazz,
            Object... parameters) {
        //
        Object[] finalParameters = parameters;
        var constructor = findObjectConstructor(clazz, ClassUtils.toClass(parameters));
        if (constructor == null) {
            // without setter
            List<Object> paramList = new ArrayList<>(Arrays.asList(parameters));
            paramList.remove(paramList.size() == 4 ? 1 : 2);
            finalParameters = paramList.toArray();
            constructor = findObjectConstructor(clazz, ClassUtils.toClass(finalParameters));
        }
        /*if (constructor == null) {
            Constructor<?>[] constructors = clazz.getConstructors();
            if (constructors.length == 1) {
                Constructor<?> firstConstructor = clazz.getConstructors()[0];
                Parameter[] params = firstConstructor.getParameters();
                if (params.length == 5) {
                    var firstParameterType = params[0].getType();
                    if (firstParameterType.isArray()) {
                        Class<?> componentType = firstParameterType.getComponentType();
                        if (componentType != null && componentType.isEnum()) {
                            Object[] enumConstants = componentType.getEnumConstants();
                            if (enumConstants != null) {
                                var allValidValues = Array.newInstance(componentType, enumConstants.length);
                                System.arraycopy(enumConstants, 0, allValidValues, 0, enumConstants.length);
                                finalParameters = new Object[5];
                                finalParameters[0] = allValidValues;
                                System.arraycopy(parameters, 0, finalParameters, 1, parameters.length);
                                constructor = findObjectConstructor(clazz, ClassUtils.toClass(finalParameters));
                            }
                        }
                    }
                }
            }
        }*/
        if (constructor == null) {
            throw new RuntimeException("Unable to find constructor for object: " + clazz.getSimpleName());
        }
        return constructor.newInstance(finalParameters);
    }

    private static @NotNull BaseCharacteristic<?> createCustomCharacteristic(
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristic hc,
            @NotNull ContextVar.Variable v) {
        HomekitCharacteristic.CharacteristicSupplier supplier = CommonUtils.newInstance(hc.impl());
        var characteristic = supplier.get(c, v);
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    private static BaseCharacteristic<?> createBooleanCharacteristic(
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristic hc,
            @NotNull ContextVar.Variable v) {
        //
        var characteristic = newCharacteristicInstance(hc.value(),
                getBooleanSupplier(v),
                setBooleanConsumer(v),
                getSubscriber(v, c, hc.type()),
                getUnsubscriber(v, c, hc.type()));
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(
            @Nullable ContextVar.Variable variable,
            @NotNull Class<T> klazz,
            @Nullable List<T> customEnumList,
            boolean invertedDefault) {
        //
        EnumMap<T, Object> map = new EnumMap<>(klazz);
        if (variable == null) {
            for (var k : klazz.getEnumConstants()) {
                map.put(k, Integer.toString(k.getCode()));
            }
            if (customEnumList != null) {
                customEnumList.addAll(map.keySet());
            }
            return map;
        }
        boolean isHomioSwitchLike = variable.getRestriction() == ContextVar.VariableType.Bool;
        boolean isHomioNumeric = variable.getRestriction() == ContextVar.VariableType.Float ||
                                 variable.getRestriction() == ContextVar.VariableType.Percentage;
        boolean itemSpecificInversion = false; // variable.getJsonData("inverted", false);
        boolean finalInversion = invertedDefault ^ itemSpecificInversion;
        T mappedOffEnumValue = null, mappedOnEnumValue = null;

        for (T k : klazz.getEnumConstants()) {
            int code = k.getCode();
            if (isHomioSwitchLike) {
                if (code == 0) {
                    map.put(k, finalInversion ? 1 : 0);
                    mappedOffEnumValue = k;
                } else if (code == 1) {
                    map.put(k, finalInversion ? 0 : 1);
                    mappedOnEnumValue = k;
                } else {
                    map.put(k, Integer.toString(code));
                }
            } else if (isHomioNumeric) {
                map.put(k, Integer.toString(code));
            } else {
                map.put(k, k.toString());
            }
        }
        if (customEnumList != null && customEnumList.isEmpty()) {
            if (isHomioSwitchLike && mappedOffEnumValue != null && mappedOnEnumValue != null) {
                customEnumList.add(mappedOffEnumValue);
                customEnumList.add(mappedOnEnumValue);
            } else {
                customEnumList.addAll(map.keySet());
            }
        }
        return map;
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(@Nullable ContextVar.Variable v, Class<T> k) {
        return createMapping(v, k, null, false);
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(@Nullable ContextVar.Variable v, Class<T> k, @Nullable List<T> l) {
        return createMapping(v, k, l, false);
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(@Nullable ContextVar.Variable v, Class<T> k, boolean i) {
        return createMapping(v, k, null, i);
    }

    public static <T> T getKeyFromMapping(@NotNull ContextVar.Variable variable,
                                          @NotNull Map<T, Object> mapping,
                                          T defaultValue) {
        var state = variable.getValue();
        String valueToMatch = state.stringValue();
        for (Map.Entry<T, Object> entry : mapping.entrySet()) {
            Object mappingConfigValue = entry.getValue();
            if (mappingConfigValue instanceof String && valueToMatch.equalsIgnoreCase((String) mappingConfigValue))
                return entry.getKey();
            if (mappingConfigValue instanceof List) {
                for (Object listItem : (List<?>) mappingConfigValue) {
                    if (valueToMatch.equalsIgnoreCase(listItem.toString())) return entry.getKey();
                }
            } else if (entry.getKey() instanceof CharacteristicEnum ce && ce.getCode() == state.intValue()) {
                return entry.getKey();
            }
        }
        return defaultValue;
    }

    public static Object getKeyFromMapping2(@NotNull ContextVar.Variable variable,
                                            @NotNull Map<Enum, Object> mapping,
                                            Object defaultValue) {
        var state = variable.getValue();
        String valueToMatch;
        switch (state) {
            case DecimalType ignored -> valueToMatch = Integer.toString(state.intValue());
            case OnOffType ignored -> valueToMatch = state.toString();
            case StringType ignored -> valueToMatch = state.toString();
            default -> {
                return defaultValue;
            }
        }
        for (Map.Entry<Enum, Object> entry : mapping.entrySet()) {
            Object mappingConfigValue = entry.getValue();
            if (mappingConfigValue instanceof String && valueToMatch.equalsIgnoreCase((String) mappingConfigValue))
                return entry.getKey();
            if (mappingConfigValue instanceof List) {
                for (Object listItem : (List<?>) mappingConfigValue) {
                    if (valueToMatch.equalsIgnoreCase(listItem.toString())) return entry.getKey();
                }
            } else if (entry.getKey() instanceof CharacteristicEnum ce && ce.getCode() == state.intValue()) {
                return entry.getKey();
            }
        }
        return defaultValue;
    }

    private static @NotNull <T extends Enum<T> & CharacteristicEnum> CompletableFuture<T> getCurrentEnumValue(@Nullable ContextVar.Variable v, Map<T, Object> m, T d) {
        return (v == null) ? completedFuture(d) : completedFuture(getKeyFromMapping(v, m, d));
    }

    /*public static <T extends Enum<T>> void setHomioVariableFromEnum(@Nullable ContextVar.Variable variable,
                                                                    T enumFromHk,
                                                                    Map<T, Object> mapping,
                                                                    @NotNull HomekitEndpointContext c) {
        if (variable == null || enumFromHk == null) return;
        Object homioValueRepresentation = mapping.get(enumFromHk);
        if (homioValueRepresentation instanceof List)
            homioValueRepresentation = ((List<?>) homioValueRepresentation).isEmpty() ? null : ((List<?>) homioValueRepresentation).getFirst();
        if (homioValueRepresentation != null) {
            String stringValueToSet = homioValueRepresentation.toString();
            try {
                if (OnOffType.ON.toString().equalsIgnoreCase(stringValueToSet) || OnOffType.OFF.toString().equalsIgnoreCase(stringValueToSet)) {
                    variable.set(OnOffType.of(stringValueToSet.toUpperCase()));
                } else if (variable instanceof DecimalType) {
                    variable.set(new DecimalType(new BigDecimal(stringValueToSet)));
                } else {
                    variable.set(new StringType(stringValueToSet));
                }
                c.updateUI();
            } catch (Exception e) {
                log.error("Error setting Homio variable {} from HomeKit enum {}: {}", variable, enumFromHk, e.getMessage());
            }
        } else {
            log.warn("No Homio mapping for HK enum {} on var {}", enumFromHk, variable);
        }
    }*/

    public static void setHomioVariableFromEnum2(
            @NotNull ContextVar.Variable variable,
            @Nullable Object enumFromHk,
            @NotNull Map<Enum, Object> mapping) {
        if (enumFromHk == null) {
            return;
        }
        Object homioValueRepresentation = mapping.get(enumFromHk);
        if (homioValueRepresentation instanceof List)
            homioValueRepresentation = ((List<?>) homioValueRepresentation).isEmpty() ? null : ((List<?>) homioValueRepresentation).getFirst();
        if (homioValueRepresentation != null) {
            String stringValueToSet = homioValueRepresentation.toString();
            try {
                if (variable.getRestriction() == ContextVar.VariableType.Bool) {
                    var value = "ON".equalsIgnoreCase(stringValueToSet) || "TRUE".equalsIgnoreCase(stringValueToSet);
                    variable.set(value);
                } else if (variable.getRestriction() == ContextVar.VariableType.Float) {
                    variable.set(new DecimalType(new BigDecimal(stringValueToSet)));
                } else {
                    variable.set(new StringType(stringValueToSet));
                }
            } catch (Exception e) {
                log.error("Error setting Homio variable {} from HomeKit enum {}: {}", variable, enumFromHk, e.getMessage());
            }
        } else {
            log.warn("No Homio mapping for HK enum {} on var {}", enumFromHk, variable);
        }
    }

    private static @NotNull Supplier<CompletableFuture<Boolean>> getBooleanSupplier(@NotNull ContextVar.Variable v) {
        return () -> completedFuture(v.getValue().boolValue());
    }

    private static @NotNull ExceptionalConsumer<Boolean> setBooleanConsumer(@NotNull ContextVar.Variable v) {
        return val -> v.set(OnOffType.of(val));
    }

    private static @NotNull Supplier<CompletableFuture<Integer>> getIntSupplier(@NotNull ContextVar.Variable v, int def) {
        return () -> completedFuture(v.getValue().intValue(def));
    }

    private static @NotNull ExceptionalConsumer<Integer> setIntConsumer(@NotNull ContextVar.Variable v) {
        return val -> v.set(new DecimalType(val));
    }

    private static @NotNull Supplier<CompletableFuture<Double>> getDoubleSupplier(@NotNull ContextVar.Variable v, double def) {
        return () -> completedFuture(v.getValue().doubleValue(def));
    }

    private static @NotNull ExceptionalConsumer<Double> setDoubleConsumer(@NotNull ContextVar.Variable v) {
        return val -> v.set(new DecimalType(val));
    }

    private static @NotNull Supplier<CompletableFuture<String>> getStringSupplier(@Nullable ContextVar.Variable v, String def) {
        return () -> completedFuture(v == null ? def : v.getValue().stringValue(def));
    }

    private static @NotNull ExceptionalConsumer<String> setStringConsumer(@NotNull ContextVar.Variable v) {
        return val -> v.set(new StringType(val));
    }

    public static @NotNull Supplier<CompletableFuture<Double>> getTemperatureSupplier(@NotNull ContextVar.Variable v, double dC) {
        return () -> completedFuture(v.getValue().doubleValue(dC));
    }

    public static @NotNull ExceptionalConsumer<Double> setTemperatureConsumer(@NotNull ContextVar.Variable v) {
        return valC -> v.set(new DecimalType(valC));
    }

    public static Consumer<HomekitCharacteristicChangeCallback> getSubscriber(
            @Nullable ContextVar.Variable v,
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristicType t) {
        return getSubscriber(v, c, t, null);
    }

    public static Consumer<HomekitCharacteristicChangeCallback> getSubscriber(
            @Nullable ContextVar.Variable v,
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristicType t,
            @Nullable Consumer<State> callback) {
        if (v == null) {
            return ignored -> {

            };
        }
        String k = c.owner().getEntityID() + "_" + c.endpoint().getId() + "_" + t.name() + "_sub";
        return cb -> {
            log.info("[{}]: Subscribe to {} - {} changes", c.owner().getEntityID(), c.endpoint().getAccessoryType(), t);
            if (cb == null) return;
            v.addListener(k, s -> {
                if (callback != null) {
                    callback.accept(s);
                }
                cb.changed();
            });
        };
    }

    public static @NotNull Runnable getUnsubscriber(
            @Nullable ContextVar.Variable v,
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristicType t) {
        if (v == null) {
            return () -> {
            };
        }
        String k = c.owner().getEntityID() + "_" + c.endpoint().getId() + "_" + t.name() + "_sub";
        return () -> {
            log.info("[{}]: Unsubscribe from {} - {} changes", c.owner().getEntityID(), c.endpoint().getAccessoryType(), t);
            v.removeListener(k);
        };
    }

    private static @NotNull <T extends Enum<T>> Supplier<CompletableFuture<T>> getEnum(
            Object value, Class<T> klazz) {
        T enumValue = Enum.valueOf(klazz, value.toString());
        return () -> completedFuture(enumValue);
    }

    private static <T extends Enum<T>> Supplier<CompletableFuture<T>> getEnum(
            Object value, Class<T> klazz, T trueValue, T falseValue) {
        if (value.equals(true) || value.equals("true")) {
            return () -> completedFuture(trueValue);
        } else if (value.equals(false) || value.equals("false")) {
            return () -> completedFuture(falseValue);
        }
        return getEnum(value, klazz);
    }
}