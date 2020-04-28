package org.touchhome.bundle.zigbee.internal;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.zsmartsystems.zigbee.IeeeAddress;

/**
 * Converter for XStream to serialise the {@link IeeeAddress} as a string
 */
public class IeeeAddressConverter implements Converter {
    @Override
    public boolean canConvert(Class clazz) {
        return clazz.equals(IeeeAddress.class);
    }

    @Override
    public void marshal(Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
        IeeeAddress address = (IeeeAddress) value;
        writer.setValue(address.toString());
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        return new IeeeAddress(reader.getValue());
    }

}
