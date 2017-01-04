package muuntaja.jackson;

import clojure.lang.IFn;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class FunctionalSerializer<T> extends StdSerializer<T> {
    private final IFn encoder;

    public FunctionalSerializer(IFn encoderFunction) {
        super(FunctionalSerializer.class, true);
        encoder = encoderFunction;
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        encoder.invoke((Object) value, (Object) gen);
    }
}
