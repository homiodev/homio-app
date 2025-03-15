package org.homio.app.json.jsog;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class JSOGRefSerializer extends JsonSerializer<JSOGRef> {

  @Override
  public void serialize(JSOGRef value, JsonGenerator jgen, SerializerProvider provider)
    throws IOException {
    if (value.used) {
      jgen.writeStartObject();
      jgen.writeObjectField(JSOGRef.REF_KEY, value.ref);
      jgen.writeEndObject();
    } else {
      value.used = true;
      jgen.writeObject(value.ref);
    }
  }

}
