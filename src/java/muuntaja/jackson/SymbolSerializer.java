package muuntaja.jackson;

import clojure.lang.Symbol;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class SymbolSerializer extends StdSerializer<Symbol> {
    public SymbolSerializer() {
        super(SymbolSerializer.class, true);
    }

    @Override
    public void serialize(Symbol value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString());
    }
}
