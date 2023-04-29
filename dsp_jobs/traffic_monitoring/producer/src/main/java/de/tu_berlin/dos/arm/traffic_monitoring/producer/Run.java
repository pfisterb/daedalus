package de.tu_berlin.dos.arm.traffic_monitoring.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tu_berlin.dos.arm.traffic_monitoring.common.data.TimeSeries;
import de.tu_berlin.dos.arm.traffic_monitoring.common.events.TrafficEvent;
import de.tu_berlin.dos.arm.traffic_monitoring.common.utils.FileParser;
import de.tu_berlin.dos.arm.traffic_monitoring.common.utils.FileReader;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.Properties;

public class Run {

    private static final Logger LOG = Logger.getLogger(Run.class);

    public static class TrafficEventSerializer implements Serializer<TrafficEvent> {

        private static final Logger LOG = Logger.getLogger(TrafficEventSerializer.class);

        private final ObjectMapper objectMap = new ObjectMapper();

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) { }

        @Override
        public byte[] serialize(String topic, TrafficEvent trafficEvent) {
            try {
                String msg = objectMap.writeValueAsString(trafficEvent);
                return msg.getBytes();
            }
            catch(JsonProcessingException ex) {
                LOG.error("Error in Serialization", ex);
            }
            return null;
        }

        @Override
        public void close() { }
    }

    public static void main(String[] args) throws Exception {

        Properties producerProps = FileReader.GET.read("producer.properties", Properties.class);

        // retrieve info about graph simulation
        String graphFileName= producerProps.getProperty("trafficGenerator.graphFileName");
        int updateInterval = Integer.parseInt(producerProps.getProperty("trafficGenerator.updateInterval"));

        // create timeseries from file
        String fileName = producerProps.getProperty("dataset.fileName");
        File file = FileReader.GET.read(fileName, File.class);
        TimeSeries ts = new TimeSeries(FileParser.GET.csv(file, "\\|", true), 86400);

        // topic to write events to
        String topic = producerProps.getProperty("kafka.topic");

        // get properties file
        Properties kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", producerProps.getProperty("kafka.brokerList"));
        kafkaProps.put("acks", "0");
        kafkaProps.put("retries", 0);
        kafkaProps.put("batch.size", 16384);
        kafkaProps.put("linger.ms", 1000);
        kafkaProps.put("buffer.memory", 33554432);
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", TrafficEventSerializer.class.getName());

        KafkaProducer<String, TrafficEvent> kafkaProducer = new KafkaProducer<>(kafkaProps);

        // initialise generation of vehicle events
        Generator.GET.generate(graphFileName, updateInterval, ts, topic, kafkaProducer);
    }
}
