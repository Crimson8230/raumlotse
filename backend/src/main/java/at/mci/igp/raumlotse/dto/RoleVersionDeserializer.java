package at.mci.igp.raumlotse.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Versions are opaque decimal text; accepting JSON numbers would hide client precision loss. */
public class RoleVersionDeserializer extends ValueDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(String.class, "Expected a text version.");
        }
        return parser.getString();
    }
}
