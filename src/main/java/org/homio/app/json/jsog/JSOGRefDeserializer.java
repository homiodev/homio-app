package org.homio.app.json.jsog;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.IOException;

public class JSOGRefDeserializer extends JsonDeserializer<JSOGRef> {

    @Override
    public JSOGRef deserialize(JsonParser jp, DeserializationContext ctx) throws IOException {
        JsonNode node = jp.readValueAsTree();
        if (node instanceof NullNode) {
            return null;
        }
        if (node.isTextual()) {
            return new JSOGRef(node.asText());
        } else {
            return new JSOGRef(node.get(JSOGRef.REF_KEY).asText());
        }
    }

}
