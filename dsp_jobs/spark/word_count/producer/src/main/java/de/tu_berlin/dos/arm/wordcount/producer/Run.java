package de.tu_berlin.dos.arm.wordcount.producer;

import de.tu_berlin.dos.arm.wordcount.common.data.TimeSeries;
import de.tu_berlin.dos.arm.wordcount.common.utils.FileParser;
import de.tu_berlin.dos.arm.wordcount.common.utils.FileReader;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Properties;

public class Run {

    private static final Logger LOG = Logger.getLogger(Run.class);

    public static void main(String[] args) throws Exception {

        // get properties file
        Properties producerProps = FileReader.GET.read("producer.properties", Properties.class);

        String fileName = producerProps.getProperty("dataset.fileName");
        File file = FileReader.GET.read(fileName, File.class);
        TimeSeries ts = new TimeSeries(FileParser.GET.csv(file, "\\|", true), 86400);
        int largest = Integer.parseInt(producerProps.getProperty("dataset.largest"));

        Properties kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", producerProps.getProperty("kafka.brokerList"));
        kafkaProps.put("acks", "0");
        kafkaProps.put("retries", 0);
        kafkaProps.put("batch.size", 16384);
        kafkaProps.put("linger.ms", 1000);
        kafkaProps.put("buffer.memory", 33554432);
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(kafkaProps);

        // initialize generation of ad events
        System.out.println("Generating lines...");
        Generator.GET.generate(ts, largest, producerProps.getProperty("kafka.topic"), kafkaProducer);
    }
}
