package muuntaja.jackson;

import clojure.lang.ITransientMap;
import clojure.lang.PersistentHashMap;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Map;

public class PersistentHashMapDeserializer extends StdDeserializer<Map<String,Object>> {
    public PersistentHashMapDeserializer() {
        super(Map.class);
    }

    @Override
    public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ITransientMap t = PersistentHashMap.EMPTY.asTransient();
        JavaType object = ctxt.constructType(Object.class);
        KeyDeserializer keyDeser = ctxt.findKeyDeserializer(object, null);
        JsonDeserializer<Object> valueDeser = ctxt.findNonContextualValueDeserializer(object);
        while (p.nextToken() != JsonToken.END_OBJECT) {
            Object key = keyDeser.deserializeKey(p.getCurrentName(), ctxt);
            p.nextToken();
            Object value = valueDeser.deserialize(p, ctxt);
            t = t.assoc(key, value);
        }
        return (Map<String,Object>)t.persistent();
    }
}
