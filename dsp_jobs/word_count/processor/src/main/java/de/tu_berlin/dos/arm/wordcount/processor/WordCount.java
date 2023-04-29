package de.tu_berlin.dos.arm.wordcount.processor;

import com.codahale.metrics.SlidingWindowReservoir;
import de.tu_berlin.dos.arm.wordcount.common.utils.FileReader;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import org.apache.flink.api.common.functions.FlatMapFunction;

public class WordCount {

    private static final Logger LOG = Logger.getLogger(WordCount.class);

    /**
     * Implements the string tokenizer that splits sentences into words as a user-defined
     * FlatMapFunction. The function takes a line (String) and splits it into multiple pairs in the
     * form of "(word,1)" ({@code Tuple2<String, Integer>}).
     */
    public static final class Tokenizer implements FlatMapFunction<String, Tuple2<String, Integer>> {

        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
            // normalize and split the line
            String[] tokens = value.toLowerCase().split("\\W+");

            // emit the pairs
            for (String token : tokens) {
                if (token.length() > 0) {
                    out.collect(new Tuple2<>(token, 1));
                }
            }
        }
    }

    public static final class ProcessLatency extends ProcessFunction<Tuple2<String, Integer>, Tuple2<String, Integer>> {

        private transient Histogram histogram;

        @Override
        public void open(Configuration parameters) {
            // measure end-to-end latency
            com.codahale.metrics.Histogram dropwizardHistogram =
                    new com.codahale.metrics.Histogram(new SlidingWindowReservoir(500));

            this.histogram = getRuntimeContext()
                    .getMetricGroup()
                    .histogram("eventTimeLag", new DropwizardHistogramWrapper(dropwizardHistogram));
        }

        @Override
        public void processElement(Tuple2<String, Integer> stringIntegerTuple2, ProcessFunction<Tuple2<String, Integer>, Tuple2<String, Integer>>.Context context, Collector<Tuple2<String, Integer>> collector) throws Exception {
            histogram.update(System.currentTimeMillis() - context.timestamp());
        }
    }


    public static void main(final String[] args) throws Exception {

        // retrieve properties from file
        Properties props = FileReader.GET.read("wc.properties", Properties.class);

        // creating map for global properties
        Map<String, String> propsMap = new HashMap<>();
        for (final String name: props.stringPropertyNames()) {
            propsMap.put(name, props.getProperty(name));
        }

        String jobName = "wordcount";
        String brokerList = props.getProperty("kafka.brokers");
        String consumerTopic = props.getProperty("kafka.consumer.topic");
        String producerTopic = props.getProperty("kafka.producer.topic");
        int checkpointInterval = 10000;

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
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(brokerList)
                .setTopics(consumerTopic)
                .setGroupId(UUID.randomUUID().toString())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // setup Kafka producer
        Properties kafkaProducerProps = new Properties();
        kafkaProducerProps.setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "30000");
        kafkaProducerProps.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        KafkaSink<Tuple2<String, Integer>> sink = KafkaSink.<Tuple2<String, Integer>>builder()
                .setBootstrapServers(brokerList)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(producerTopic)
                        .setValueSerializationSchema((SerializationSchema<Tuple2<String, Integer>>) e -> {
                            return String.format("(\"%s\", %d)", e.f0, e.f1).getBytes(StandardCharsets.UTF_8);
                        })
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .setTransactionalIdPrefix(RandomStringUtils.random(10, true, false))
                .setKafkaProducerConfig(kafkaProducerProps)
                .build();

        // create direct kafka stream
        DataStream<String> messageStream =
            env.fromSource(
                    source,
                    WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(2)),
        "KafkaSource");


        messageStream
            .flatMap(new Tokenizer())
            .name("tokenizer")
            // keyBy groups tuples based on the "0" field, the word.
            // Using a keyBy allows performing aggregations and other
            // stateful transformations over data on a per-key basis.
            // This is similar to a GROUP BY clause in a SQL query.
            .keyBy(value -> value.f0)
            // For each key, we perform a simple sum of the "1" field, the count.
            // If the input data stream is bounded, sum will output a final count for
            // each word. If it is unbounded, it will continuously output updates
            // each time it sees a new instance of each word in the stream.
            .sum(1)
            .name("counter")
            .process(new ProcessLatency())
            .name("latency");

        ;

            //.sinkTo(sink)
            //.name("KafkaSink-" + RandomStringUtils.random(10, true, true));

        env.execute(jobName);
    }
}
