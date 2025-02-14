package com.practice.java.util;

import com.practice.java.parquetbatch.avro.Person;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class ParquetItemWriter2 implements ItemWriter<Person> {

    private static final String PARQUET_FILE_PATH = "data/persons.parquet";

    @Override
    public void write(List<? extends Person> persons) throws Exception {
        File parquetFile = new File(PARQUET_FILE_PATH);
        boolean appendMode = parquetFile.exists();

        Path filePath = new Path(PARQUET_FILE_PATH);

        // Directly use Avro's SpecificRecord API (no manual mapping needed)
        try (AvroParquetWriter<Person> writer = AvroParquetWriter
                .<Person>builder(filePath)
                .withSchema(Person.getClassSchema())  // Use Avro schema
                .withDataModel(org.apache.avro.specific.SpecificData.get())  // Avro-specific data model
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withWriteMode(appendMode ? ParquetFileWriter.Mode.APPEND : ParquetFileWriter.Mode.OVERWRITE)
                .build()) {

            for (Person person : persons) {
                writer.write(person);  // Directly write the Avro object
            }
        }
    }
}

