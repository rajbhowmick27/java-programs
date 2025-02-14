package com.practice.java.util;

import com.practice.java.parquetbatch.avro.Person;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Component
public class ParquetItemWriter implements ItemWriter<Person> {

    private static final String PARQUET_FILE_PATH = "data/persons.parquet";

    @Override
    public void write(List<? extends Person> persons) throws Exception {
        File parquetFile = new File(PARQUET_FILE_PATH);
        boolean appendMode = parquetFile.exists();  // Check if file exists

        Path filePath = new Path(PARQUET_FILE_PATH);
        Schema schema = Person.getClassSchema();

        // Configure Parquet writer to append or create new
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter
                .<GenericRecord>builder(filePath)
                .withSchema(schema)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withWriteMode(appendMode ? ParquetFileWriter.Mode.APPEND : ParquetFileWriter.Mode.OVERWRITE)
                .build()) {

            for (Person person : persons) {
                GenericRecord record = new GenericData.Record(schema);
                record.put("id", person.getId());
                record.put("name", person.getName());
                record.put("age", person.getAge());

                GenericRecord addressRecord = new GenericData.Record(schema.getField("address").schema());
                addressRecord.put("city", person.getAddress().getCity());
                addressRecord.put("state", person.getAddress().getState());
                addressRecord.put("country", person.getAddress().getCountry());
                record.put("address", addressRecord);

                record.put("phoneNumbers", person.getPhoneNumbers());

                writer.write(record);
            }
        }
    }

    public byte[] getParquetFileBytes() throws IOException {
        return Files.readAllBytes(new File(PARQUET_FILE_PATH).toPath());
    }
}

