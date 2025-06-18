package org.homio.addon.homekit;

import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.CharacteristicEnum;
import io.github.hapjava.characteristics.ExceptionalConsumer;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.base.*;
import io.github.hapjava.characteristics.impl.thermostat.TemperatureDisplayUnitCharacteristic;
import io.github.hapjava.characteristics.impl.thermostat.TemperatureDisplayUnitEnum;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.homekit.accessories.Characteristics;
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

@Log4j2
public class HomekitCharacteristicFactory {

    @SneakyThrows
    public static void buildCharacteristics(@NotNull HomekitEndpointContext context, @NotNull Characteristics characteristics) {
        String accessoryType = context.endpoint().getAccessoryType().name();
        for (Method method : HomekitEndpointEntity.class.getDeclaredMethods()) {
            var hc = findCorrectHomekitCCharacteristic(method, context.endpoint().getAccessoryType().name());
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
            String varValue = (String) method.invoke(context.endpoint());
            if (StringUtils.isEmpty(varValue)) {
                continue;
            }
            if (characteristics.has(hc.type())) {
                continue;
            }
            var characteristicClass = hc.value();
            ContextVar.Variable v = context.getVariable(varValue);
            if (v == null) {
                if (required) {
                    throw new RuntimeException("Missing required variable: " + varValue + " for accessory: " + accessoryType);
                }
                continue;
            }
            if (!hc.impl().equals(HomekitCharacteristic.DefaultSupplier.class)) {
                characteristics.addIfNotNull(hc.type(), createCustomCharacteristic(context, hc, v));
            } else if (BooleanCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createBooleanCharacteristic(context, hc, v));
            } else if (IntegerCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createIntegerCharacteristic(context, hc, v));
            } else if (EnumCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createEnumCharacteristic(context, hc, v));
            } else if (FloatCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createFloatCharacteristic(context, hc, v));
            } else if (StringCharacteristic.class.isAssignableFrom(characteristicClass)) {
                characteristics.addIfNotNull(hc.type(), createStringCharacteristic(context, hc, v));
            } else {
                throw new RuntimeException("Unable to find handler for characteristic: " + characteristicClass);
            }
        }
    }

    private static HomekitCharacteristic findCorrectHomekitCCharacteristic(Method method, String accessoryName) {
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

    private static Characteristic createEnumCharacteristic(HomekitEndpointContext c,
                                                           HomekitCharacteristic hc,
                                                           ContextVar.Variable v) {
        Type superType = hc.value().getGenericSuperclass();
        if (superType instanceof ParameterizedType) {
            Type enumType = ((ParameterizedType) superType).getActualTypeArguments()[0];

            if (enumType instanceof Class && ((Class<?>) enumType).isEnum()) {
                Class enumClass = (Class<?>) enumType;
                Map<Enum, Object> m = createMapping(v, enumClass);
                String defValue = hc.defaultStringValue();
                Object defaultEnum = StringUtils.isEmpty(defValue)
                        ? enumClass.getEnumConstants()[0]
                        : Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), defValue);

                BaseCharacteristic<?> characteristic = newCharacteristicInstance(
                        hc.value(),
                        (Supplier<CompletableFuture<?>>) () -> completedFuture(getKeyFromMapping2(v, m, defaultEnum)),
                        (ExceptionalConsumer<?>) newVal -> {
                            setHomioVariableFromEnum2(v, newVal, m);
                            // setIntConsumer(v, c),
                        },
                        getSubscriber(v, c, hc.type()),
                        getUnsubscriber(v, c, hc.type()));

                c.setCharacteristic(characteristic, v, hc.type());
                return characteristic;
            }
        }
        return null;
    }

    private static Characteristic createStringCharacteristic(HomekitEndpointContext c,
                                                             HomekitCharacteristic hc,
                                                             ContextVar.Variable v) {
        var characteristic = newCharacteristicInstance(hc.value(),
                getStringSupplier(v, hc.defaultStringValue()),
                setStringConsumer(v),
                getSubscriber(v, c, hc.type()),
                getUnsubscriber(v, c, hc.type()));
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    private static Characteristic createFloatCharacteristic(HomekitEndpointContext c,
                                                            HomekitCharacteristic hc,
                                                            ContextVar.Variable v) {
        var characteristic = newCharacteristicInstance(hc.value(),
                getDoubleSupplier(v, hc.defaultDoubleValue()),
                setDoubleConsumer(v),
                getSubscriber(v, c, hc.type()),
                getUnsubscriber(v, c, hc.type()));
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    private static Characteristic createIntegerCharacteristic(HomekitEndpointContext c,
                                                              HomekitCharacteristic hc,
                                                              ContextVar.Variable v) {
        var characteristic = newCharacteristicInstance(hc.value(),
                getIntSupplier(v, hc.defaultIntValue()),
                setIntConsumer(v),
                getSubscriber(v, c, hc.type()),
                getUnsubscriber(v, c, hc.type()));
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    @SneakyThrows
    private static BaseCharacteristic newCharacteristicInstance(Class<? extends BaseCharacteristic> clazz, Object... parameters) {
        Object[] finalParameters = parameters;
        var constructor = findObjectConstructor(clazz, ClassUtils.toClass(parameters));
        if (constructor == null) {
            // without setter
            List<Object> paramList = new ArrayList<>(Arrays.asList(parameters));
            paramList.remove(1);
            finalParameters = paramList.toArray();
            constructor = findObjectConstructor(clazz, ClassUtils.toClass(finalParameters));
        }
        if (constructor == null) {
            throw new RuntimeException("Unable to find constructor for object: " + clazz.getSimpleName());
        }
        return constructor.newInstance(finalParameters);
    }

    private static BaseCharacteristic<?> createCustomCharacteristic(HomekitEndpointContext c,
                                                                    HomekitCharacteristic hc,
                                                                    ContextVar.Variable v) {
        HomekitCharacteristic.CharacteristicSupplier supplier = CommonUtils.newInstance(hc.impl());
        var characteristic = supplier.get(c, v);
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    private static BaseCharacteristic<?> createBooleanCharacteristic(HomekitEndpointContext c,
                                                                     HomekitCharacteristic hc,
                                                                     ContextVar.Variable v) {
        var characteristic = newCharacteristicInstance(hc.value(),
                getBooleanSupplier(v),
                setBooleanConsumer(v),
                getSubscriber(v, c, hc.type()),
                getUnsubscriber(v, c, hc.type()));
        c.setCharacteristic(characteristic, v, hc.type());
        return characteristic;
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(
            @Nullable ContextVar.Variable variable, Class<T> klazz,
            @Nullable List<T> customEnumList, boolean invertedDefault) {
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

    public static <T> T getKeyFromMapping(ContextVar.Variable variable, Map<T, Object> mapping, T defaultValue) {
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

    public static Object getKeyFromMapping2(ContextVar.Variable variable, Map<Enum, Object> mapping, Object defaultValue) {
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

    public static void setHomioVariableFromEnum2(@Nullable ContextVar.Variable variable,
                                                 Object enumFromHk,
                                                 Map<Enum, Object> mapping) {
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
            @NotNull ContextVar.Variable v,
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristicType t) {
        return getSubscriber(v, c, t, null);
    }

    public static Consumer<HomekitCharacteristicChangeCallback> getSubscriber(
            @NotNull ContextVar.Variable v,
            @NotNull HomekitEndpointContext c,
            @NotNull HomekitCharacteristicType t,
            @Nullable Consumer<State> callback) {
        String k = c.owner().getEntityID() + "_" + c.endpoint().getId() + "_" + t.name() + "_sub";
        return cb -> {
            if (cb == null) return;
            v.addListener(k, s -> {
                if (callback != null) {
                    callback.accept(s);
                }
                cb.changed();
            });
        };
    }

    public static Runnable getUnsubscriber(@Nullable ContextVar.Variable v, HomekitEndpointContext c, HomekitCharacteristicType t) {
        if (v == null) return () -> {
        };
        String k = c.owner().getEntityID() + "_" + c.endpoint().getId() + "_" + t.name() + "_sub";
        return () -> v.removeListener(k);
    }

    public static TemperatureDisplayUnitCharacteristic createSystemTemperatureDisplayUnitCharacteristic() {
        return new TemperatureDisplayUnitCharacteristic(() -> CompletableFuture
                .completedFuture(/*HomekitCharacteristicFactory.useFahrenheit() ? TemperatureDisplayUnitEnum.FAHRENHEIT
                        : */TemperatureDisplayUnitEnum.CELSIUS),
                (value) -> {
                }, (cb) -> {
        }, () -> {
        });
    }

    private static <T extends Enum<T>> Supplier<CompletableFuture<T>> getEnum(
            Object value, Class<T> klazz) {
        T enumValue = Enum.valueOf(klazz, value.toString());
        return () -> CompletableFuture.completedFuture(enumValue);
    }

    private static <T extends Enum<T>> Supplier<CompletableFuture<T>> getEnum(
            Object value, Class<T> klazz, T trueValue, T falseValue) {
        if (value.equals(true) || value.equals("true")) {
            return () -> CompletableFuture.completedFuture(trueValue);
        } else if (value.equals(false) || value.equals("false")) {
            return () -> CompletableFuture.completedFuture(falseValue);
        }
        return getEnum(value, klazz);
    }
}