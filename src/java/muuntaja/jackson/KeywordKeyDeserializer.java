package muuntaja.jackson;

import clojure.lang.Keyword;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

import java.io.IOException;

public class KeywordKeyDeserializer extends KeyDeserializer {
    public KeywordKeyDeserializer() {
        super();
    }

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return Keyword.intern(key);
    }
}
