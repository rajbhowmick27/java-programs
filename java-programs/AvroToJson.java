import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class AvroToBase64 {
    public static String toBase64(SpecificRecordBase record) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SpecificDatumWriter<SpecificRecordBase> writer = new SpecificDatumWriter<>(record.getSchema());
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(record, encoder);
        encoder.flush();
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
