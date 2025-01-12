package org.homio.app.service.mem;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

public class ObjectCodec implements Codec<Object> {

  @Override
  public Object decode(BsonReader reader, DecoderContext decoderContext) {
    switch (reader.getCurrentBsonType()) {
      case INT32:
        return reader.readInt32();
      case INT64:
        return reader.readInt64();
      case BOOLEAN:
        return reader.readBoolean();
      case DOUBLE:
        return reader.readDouble();
      case STRING:
        return reader.readString();
    }
    throw new RuntimeException("Unable to find corresponding decoder for type: " + reader.getCurrentBsonType());
  }

  @Override
  public void encode(BsonWriter writer, Object value, EncoderContext encoderContext) {
    if (value instanceof String) {
      writer.writeString((String) value);
    } else if (value instanceof Integer) {
      writer.writeInt32((Integer) value);
    } else if (value instanceof Boolean) {
      writer.writeBoolean((Boolean) value);
    } else if (value instanceof Long) {
      writer.writeInt64((Long) value);
    } else if (Number.class.isAssignableFrom(value.getClass())) {
      writer.writeDouble(((Number) value).doubleValue());
    } else {
      throw new RuntimeException("Unable to find corresponding encoder for type: " + value.getClass().getSimpleName());
    }
  }

  @Override
  public Class<Object> getEncoderClass() {
    return Object.class;
  }
}
