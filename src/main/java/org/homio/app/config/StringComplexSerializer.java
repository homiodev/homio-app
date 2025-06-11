package org.homio.app.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.entity.BaseEntity;
import org.homio.api.ui.field.UIFieldLinkToRoute;
import org.homio.app.manager.common.ContextImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class StringComplexSerializer extends StdSerializer<String> implements ContextualSerializer {

  protected StringComplexSerializer() {
    super(String.class);
  }

  private static @NotNull JsonSerializer<String> createFieldLinkSerializer() {
    return new JsonSerializer<>() {
      @Override
      public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        BaseEntity entity = ContextImpl.INSTANCE == null ? null : ContextImpl.INSTANCE.db().get(value);
        if (entity == null) {
          gen.writeString(value);
        } else {
          String title = entity.getTitle();
          if (entity instanceof UIFieldLinkToRoute.FieldLinkToEntityTitleProvider) {
            title = ((UIFieldLinkToRoute.FieldLinkToEntityTitleProvider) entity).getLinkTitle();
          }
          gen.writeString("%s###{\"title\":\"%s\"}".formatted(value, title));
        }
      }
    };
  }

  @Override
  public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
    // Check if the field has the ReferenceToBean annotation
    if (property != null) {
      UIFieldLinkToRoute annotation = property.getAnnotation(UIFieldLinkToRoute.class);
      if (annotation != null && annotation.applyTitle()) {
        return createFieldLinkSerializer();
      }
    }
    return this;
  }

  @Override
  public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
    gen.writeString(value);
  }

  @Override
  public boolean isEmpty(SerializerProvider provider, String value) {
    return StringUtils.isEmpty(value);
  }
}
