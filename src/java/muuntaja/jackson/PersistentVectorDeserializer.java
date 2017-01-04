package muuntaja.jackson;

import clojure.lang.ITransientCollection;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.List;

public class PersistentVectorDeserializer extends StdDeserializer<List<Object>> {
    public PersistentVectorDeserializer() {
        super(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ITransientCollection t = PersistentVector.EMPTY.asTransient();
        JsonDeserializer<Object> deser = ctxt.findNonContextualValueDeserializer(ctxt.constructType(Object.class));
        while (p.nextValue() != JsonToken.END_ARRAY) {
            t = t.conj(deser.deserialize(p, ctxt));
        }
        // t.persistent() returns a PersistentVector which is a list
        return (List<Object>)t.persistent();
    }
}
