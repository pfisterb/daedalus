package de.tu_berlin.dos.arm.traffic_monitoring.processor;

import com.codahale.metrics.SlidingWindowReservoir;
import de.tu_berlin.dos.arm.traffic_monitoring.common.events.Point;
import de.tu_berlin.dos.arm.traffic_monitoring.common.events.TrafficEvent;
import de.tu_berlin.dos.arm.traffic_monitoring.common.utils.FileReader;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.flink.api.common.eventtime.*;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.log4j.Logger;
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper;
import org.apache.flink.metrics.Histogram;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class Run {

    // traffic events are at most 60 sec out-of-order.
    private static final int MAX_EVENT_DELAY = 60;
    private static final Logger LOG = Logger.getLogger(Run.class);

    // class to filter traffic events within point of interest
    public static class POIFilter implements FilterFunction<TrafficEvent> {

        public final Point point;
        public final int radius;

        public POIFilter(Point point, int radius) {

            this.point = point;
            this.radius = radius;
        }

        @Override
        public boolean filter(TrafficEvent event) throws Exception {

            // Use Geodesic Inverse function to find distance in meters
            GeodesicData g1 = Geodesic.WGS84.Inverse(
                point.lt,
                point.lg,
                event.getPt().lt,
                event.getPt().lg);
            // determine if it is in the radius of the POE or not
            return g1.s12 <= radius;
        }
    }

    // Window to aggregate traffic events and calculate average speed in km/h
    public static class AvgSpeedWindow extends ProcessWindowFunction<TrafficEvent, Tuple5<Long, String, Float, Float, Integer>, String, TimeWindow> {

        public final int updateInterval;
        private transient Histogram histogram;

        public AvgSpeedWindow(int updateInterval) {

            this.updateInterval = updateInterval;
        }


        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            com.codahale.metrics.Histogram dropwizardHistogram =
                    new com.codahale.metrics.Histogram(new SlidingWindowReservoir(500));
            this.histogram = getRuntimeContext()
                    .getMetricGroup()
                    .histogram("eventTimeLag", new DropwizardHistogramWrapper(dropwizardHistogram));
        }

        @Override
        public void process(
                String vehicleId,
                Context context,
                Iterable<TrafficEvent> events,
                Collector<Tuple5<Long, String, Float, Float, Integer>> out) {

            Point previous = null;
            double distance = 0;
            int count = 0;
            for (TrafficEvent event : events) {
                if (previous != null) {
                    GeodesicData g1 = Geodesic.WGS84.Inverse(
                        previous.lt,
                        previous.lg,
                        event.getPt().lt,
                        event.getPt().lg);
                    distance += g1.s12;
                    count++;
                }
                previous = event.getPt();
            }
            // calculate time in hours
            double time = (count * updateInterval) / 3600000d;
            int avgSpeed = 0;
            if (time != 0) avgSpeed = (int) ((distance/1000) / time);
            if (previous != null) {
                histogram.update(context.currentProcessingTime() - context.currentWatermark());
                out.collect(new Tuple5<>(context.window().getEnd(), vehicleId, previous.lt, previous.lg, avgSpeed));
            }
        }
    }

    // filter to determine if traffic vehicle is traveling over the speed limit
    public static class SpeedingFilter implements FilterFunction<Tuple5<Long, String, Float, Float, Integer>> {

        public final int speedLimit;

        public SpeedingFilter(int speedLimit) {

            this.speedLimit = speedLimit;
        }

        @Override
        public boolean filter(Tuple5<Long, String, Float, Float, Integer> trafficVehicle) throws Exception {

            return trafficVehicle.f4 >= speedLimit;
        }
    }

    // Retrieve vehicle type from database and parse json, builder is parsed to stop serialization error
    public static class VehicleEnricher extends RichMapFunction<Tuple5<Long, String, Float, Float, Integer>, String> {

        public VehicleEnricher() {
        }

        @Override
        public String map(Tuple5<Long, String, Float, Float, Integer> value) throws Exception {

            return String.format(
                "{ts: %d, lp: '%s', lat: %f, long: %f, avgSpeed: %d}",
                value.f0, value.f1, value.f2, value.f3, value.f4);
        }
    }

    public static void main(String[] args) throws Exception {


        // retrieve properties from file
        Properties props = FileReader.GET.read("processor.properties", Properties.class);

        // creating map for global properties
        Map<String, String> propsMap = new HashMap<>();
        for (final String name: props.stringPropertyNames()) {
            propsMap.put(name, props.getProperty(name));
        }

        String jobName = "traffic_monitoring";
        String brokerList = props.getProperty("kafka.brokers");
        String consumerTopic = props.getProperty("kafka.consumer.topic");
        String producerTopic = props.getProperty("kafka.producer.topic");
        int checkpointInterval = 10000;

        int updateInterval = Integer.parseInt(props.getProperty("traffic.updateInterval"));
        int speedLimit = Integer.parseInt(props.getProperty("traffic.speedLimit"));

        // set up streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setBufferTimeout(-1);

        // setting global properties from file
        env.getConfig().setGlobalJobParameters(ParameterTool.fromMap(propsMap));

        // configuring RocksDB state backend to use HDFS
        String backupFolder = props.getProperty("hdfs.backupFolder");
        env.setStateBackend(new EmbeddedRocksDBStateBackend());
        env.getCheckpointConfig().setCheckpointStorage(backupFolder);

        // start a checkpoint based on supplied interval
        env.enableCheckpointing(checkpointInterval);

        // set mode to exactly-once (this is the default)
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

        // checkpoints have to complete within two minute, or are discarded
        env.getCheckpointConfig().setCheckpointTimeout(3600000);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);

        // enable externalized checkpoints which are deleted after job cancellation
        env.getCheckpointConfig().enableExternalizedCheckpoints(ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION);

        // no external services which could take some time to respond, therefore 1
        // allow only one checkpoint to be in progress at the same time
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(100);

        env.disableOperatorChaining();


        // setup Kafka consumer
        KafkaSource<TrafficEvent> source = KafkaSource.<TrafficEvent>builder()
                .setBootstrapServers(brokerList)
                .setTopics(consumerTopic)
                .setGroupId(UUID.randomUUID().toString())
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TrafficEventSchema())
                .build();

        // setup Kafka producer
        Properties kafkaProducerProps = new Properties();
        kafkaProducerProps.setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "30000");
        kafkaProducerProps.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
        //kafkaProducerProps.setProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG, UUID.randomUUID().toString());
        //kafkaProducerProps.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        //kafkaProducerProps.setProperty(ProducerConfig.LINGER_MS_CONFIG, "1000");
        //kafkaProducerProps.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        //kafkaProducerProps.setProperty(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");


        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(brokerList)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(producerTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .setTransactionalIdPrefix(RandomStringUtils.random(10, true, false))
                .setKafkaProducerConfig(kafkaProducerProps)
                .build();


        // configure event-time and watermarks (requires milliseconds)
/*        WatermarkStrategy<TrafficEvent> watermarkStrategy =
                WatermarkStrategy.<TrafficEvent>forBoundedOutOfOrderness((Duration.ofSeconds(2)))
                        .withTimestampAssigner((event, timestamp) -> event.getTs() * 1000);*/

        // create direct kafka stream
        DataStream<TrafficEvent> messageStream =
            env.fromSource(source,
                    WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(2)),
        "KafkaSource");

        // Point of interest
        Point point = new Point(52.51623f, 13.38532f); // centroid
        messageStream
            .filter(new POIFilter(point, 1000))
            .name("POIFilter")
            .keyBy(TrafficEvent::getLp)
            .window(TumblingEventTimeWindows.of(Time.seconds(10), Time.seconds(1)))
            .process(new AvgSpeedWindow(updateInterval))
            .name("AvgSpeedWindow")
/*            .filter(new SpeedingFilter(speedLimit))
            .name("SpeedFilter")*/
            .map(new VehicleEnricher())
            .name("VehicleEnricher")
            .sinkTo(sink)
            .name("KafkaSink-" + RandomStringUtils.random(10, true, true));

        env.execute(jobName);
    }
}
