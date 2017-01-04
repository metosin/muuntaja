package muuntaja.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Date;
import java.util.SimpleTimeZone;
import java.text.SimpleDateFormat;

public class DateSerializer extends StdSerializer<Date> {
    private final SimpleDateFormat formatter;

    public DateSerializer() {
        super(DateSerializer.class, true);
        formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        formatter.setTimeZone(new SimpleTimeZone(0, "UTC"));
    }

    @Override
    public void serialize(Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        // SimpleDateFormat is not thread-safe, so we must synchronize it.
        synchronized(formatter) {
            gen.writeString(formatter.format(value));
        }
    }
}
