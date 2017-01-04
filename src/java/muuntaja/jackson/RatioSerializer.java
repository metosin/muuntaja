package muuntaja.jackson;

import clojure.lang.Ratio;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class RatioSerializer extends StdSerializer<Ratio> {
    public RatioSerializer() {
        super(RatioSerializer.class, true);
    }

    @Override
    public void serialize(Ratio value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeNumber(value.doubleValue());
    }
}
