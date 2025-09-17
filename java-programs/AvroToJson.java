import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

import java.io.ByteArrayOutputStream;

public class AvroToJson {
    public static String toJson(SpecificRecordBase record) throws Exception {
        Schema schema = record.getSchema();
        DatumWriter<SpecificRecordBase> writer = new SpecificDatumWriter<>(schema);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().jsonEncoder(schema, out);
        writer.write(record, encoder);
        encoder.flush();
        return out.toString("UTF-8");
    }
}
