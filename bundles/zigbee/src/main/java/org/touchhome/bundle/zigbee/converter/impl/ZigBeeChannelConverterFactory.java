package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.util.ClassFinder;
import org.touchhome.bundle.zigbee.ZigBeeCoordinatorHandler;
import org.touchhome.bundle.zigbee.ZigBeeDevice;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.requireEndpoint.RequireEndpoint;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Log4j2
@Component
public final class ZigBeeChannelConverterFactory {

    /**
     * Map of all zigbeeRequireEndpoints supported by the binding
     */
    private final Map<ZigBeeConverter, Class<? extends ZigBeeBaseChannelConverter>> channelMap;

    private final List<Class<? extends ZigBeeBaseChannelConverter>> converters;

    public ZigBeeChannelConverterFactory(ClassFinder classFinder) {
        converters = classFinder.getClassesWithAnnotation(ZigBeeConverter.class);

        channelMap = converters.stream().collect(Collectors
                .toMap((Function<Class, ZigBeeConverter>) aClass -> AnnotationUtils.getAnnotation(aClass, ZigBeeConverter.class), c -> c));
    }

    @SneakyThrows
    public Collection<ZigBeeConverterEndpoint> getZigBeeConverterEndpoints(ZigBeeEndpoint endpoint) {
        Map<String, ZigBeeConverterEndpoint> zigBeeEndpoints = new HashMap<>();

        for (Map.Entry<ZigBeeConverter, Class<? extends ZigBeeBaseChannelConverter>> converterEntry : channelMap.entrySet()) {
            ZigBeeBaseChannelConverter converter = converterEntry.getValue().getConstructor().newInstance();
            if (converter.acceptEndpoint(endpoint)) {
                ZigBeeConverterEndpoint zigBeeConverterEndpoint = new ZigBeeConverterEndpoint(converterEntry.getKey(), endpoint.getIeeeAddress().toString(), endpoint.getEndpointId());
                zigBeeEndpoints.put(zigBeeConverterEndpoint.getClusterName(), zigBeeConverterEndpoint);
            }
        }

        // Remove ON/OFF if we support LEVEL
        if (zigBeeEndpoints.containsKey("zigbee:switch_level")) {
            zigBeeEndpoints.remove("zigbee:switch_onoff");
        }

        // Remove LEVEL if we support COLOR
        if (zigBeeEndpoints.containsKey("zigbee:color_color")) {
            zigBeeEndpoints.remove("zigbee:switch_onoff");
        }

        return zigBeeEndpoints.values();
    }

    public ZigBeeBaseChannelConverter createConverter(ZigBeeDevice zigBeeDevice, ZigBeeConverterEndpoint zigBeeConverterEndpoint,
                                                      ZigBeeCoordinatorHandler coordinatorHandler, IeeeAddress ieeeAddress) {
        Constructor<? extends ZigBeeBaseChannelConverter> constructor;
        try {
            constructor = channelMap.get(zigBeeConverterEndpoint.getZigBeeConverter()).getConstructor();
            ZigBeeBaseChannelConverter instance = constructor.newInstance();

            instance.initialize(zigBeeDevice, zigBeeConverterEndpoint, coordinatorHandler, ieeeAddress, zigBeeConverterEndpoint.getEndpointId());
            return instance;
        } catch (Exception e) {
            log.error("{}: Unable to create channel {}", ieeeAddress, zigBeeConverterEndpoint, e);
        }

        return null;
    }

    public Set<Integer> getImplementedClientClusters() {
        return channelMap.keySet().stream().flatMapToInt(c -> IntStream.of(c.clientClusters()))
                .boxed().collect(Collectors.toSet());
    }

    public Set<Integer> getImplementedServerClusters() {
        return channelMap.keySet().stream().flatMapToInt(c -> IntStream.of(c.serverClusters()))
                .boxed().collect(Collectors.toSet());
    }

    public List<ZigBeeConverterEndpoint> createConverterEndpoint(RequireEndpoint re, String ieeeAddress) {
        return channelMap.keySet().stream().filter(re::match).map(zigBeeConverter ->
                new ZigBeeConverterEndpoint(zigBeeConverter, ieeeAddress, re.getEndpoint())).collect(Collectors.toList());
    }
}
