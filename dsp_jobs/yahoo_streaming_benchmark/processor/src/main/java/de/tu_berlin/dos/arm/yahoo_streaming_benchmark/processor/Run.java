package de.tu_berlin.dos.arm.yahoo_streaming_benchmark.processor;

import com.codahale.metrics.SlidingWindowReservoir;
import de.tu_berlin.dos.arm.yahoo_streaming_benchmark.common.utils.FileReader;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
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
import org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;


public class Run {

    private static final Logger LOG = Logger.getLogger(Run.class);

    public static class EventFilterBolt implements FilterFunction<AdEvent> {

        @Override
        public boolean filter(AdEvent adEvent) throws Exception {
            return adEvent.getEt().equals("view");
        }
    }

    public static class EventMapper implements MapFunction<AdEvent, Tuple2<String, Long>> {



        @Override
        public Tuple2<String, Long> map(AdEvent adEvent) throws Exception {
            return new Tuple2<>(adEvent.getId(), adEvent.getTs());
        }
    }

    public static final class RedisJoinBolt extends RichFlatMapFunction<Tuple2<String, Long>, Tuple3<String, String, Long>> {

        transient RedisAdCampaignCache redisAdCampaignCache;

        @Override
        public void open(Configuration parameters) {
            //initialize jedis
            ParameterTool parameterTool = (ParameterTool) getRuntimeContext().getExecutionConfig().getGlobalJobParameters();
            parameterTool.getRequired("redis.host");
            LOG.info("Opening connection with Jedis to " + parameterTool.getRequired("redis.host"));
            this.redisAdCampaignCache = new RedisAdCampaignCache(parameterTool.getRequired("redis.host"));
            this.redisAdCampaignCache.prepare();
        }

        @Override
        public void flatMap(Tuple2<String, Long> input, Collector<Tuple3<String, String, Long>> out) throws Exception {

            String ad_id = input.getField(0);
            String campaign_id = this.redisAdCampaignCache.execute(ad_id);
            if (campaign_id == null) {
                campaign_id = "UNKNOWN";
                //return;
            }

            // <campaign-id, ad-id, ad-ts>
            Tuple3<String, String, Long> tuple = new Tuple3<>(campaign_id, input.getField(0), input.getField(1));
            out.collect(tuple);
        }
    }


    public static class CampaignProcessor extends ProcessWindowFunction<Tuple3<String, String, Long>, Tuple3<String, Long, Long>, String, TimeWindow> {

        private String redisServerHostname;
        private static final long serialVersionUID = 1L;

        private transient Histogram histogram;

        @Override
        public void open(Configuration parameters) throws Exception {

            ParameterTool parameterTool = (ParameterTool) getRuntimeContext().getExecutionConfig().getGlobalJobParameters();
            parameterTool.getRequired("redis.host");
            LOG.info("Opening connection with Jedis to " + parameterTool.getRequired("redis.host"));

            redisServerHostname = parameterTool.getRequired("redis.host");

            // measure end-to-end latency
            com.codahale.metrics.Histogram dropwizardHistogram =
                    new com.codahale.metrics.Histogram(new SlidingWindowReservoir(500));

            this.histogram = getRuntimeContext()
                    .getMetricGroup()
                    .histogram("eventTimeLag", new DropwizardHistogramWrapper(dropwizardHistogram));

        }

        @Override
        public void process(String s, ProcessWindowFunction<Tuple3<String, String, Long>, Tuple3<String, Long, Long>, String, TimeWindow>.Context context, Iterable<Tuple3<String, String, Long>> elements, Collector<Tuple3<String, Long, Long>> out) throws Exception {

            // get campaign key
            String campaign_id = s;
            // count the number of ads for this campaign
            Iterator<Tuple3<String, String, Long>> iterator = elements.iterator();
            long count = 0;
            while (iterator.hasNext()) {
                count++;
                histogram.update(System.currentTimeMillis() - context.window().getEnd());
                iterator.next();
            }

            // create output of operator
            out.collect(new Tuple3<>(campaign_id, count, context.window().getEnd()));


        }
    }


    public static void main(final String[] args) throws Exception {

        // retrieve properties from file
        Properties props = FileReader.GET.read("advertising.properties", Properties.class);

        // creating map for global properties
        Map<String, String> propsMap = new HashMap<>();
        for (final String name: props.stringPropertyNames()) {
            propsMap.put(name, props.getProperty(name));
        }

        String jobName = "advertising";
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
        KafkaSource<AdEvent> source = KafkaSource.<AdEvent>builder()
                .setBootstrapServers(brokerList)
                .setTopics(consumerTopic)
                .setGroupId(UUID.randomUUID().toString())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new AdEventSchema())
                .build();

        // setup Kafka producer
        Properties kafkaProducerProps = new Properties();
        kafkaProducerProps.setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "30000");
        kafkaProducerProps.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        KafkaSink<Tuple3<String, Long, Long>> sink = KafkaSink.<Tuple3<String, Long, Long>>builder()
                .setBootstrapServers(brokerList)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(producerTopic)
                        .setValueSerializationSchema((SerializationSchema<Tuple3<String, Long, Long>>) e -> {
                            return String.format("{\"id\":\"%s\",\"ct\":%d,\"ts\":%d}", e.f0, e.f1, e.f2).getBytes(StandardCharsets.UTF_8);
                        })
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .setTransactionalIdPrefix(RandomStringUtils.random(10, true, false))
                .setKafkaProducerConfig(kafkaProducerProps)
                .build();

        // configure event-time and watermarks (requires milliseconds)
        WatermarkStrategy<AdEvent> watermarkStrategy =
                WatermarkStrategy.<AdEvent>forBoundedOutOfOrderness((Duration.ofSeconds(2)))
                        .withTimestampAssigner((event, timestamp) -> event.getTs() * 1000);

        // create direct kafka stream
        DataStream<AdEvent> messageStream =
            env.fromSource(source,
                    watermarkStrategy,
        "KafkaSource");

        messageStream
            // filter the records if event type is "view"
            .filter(new EventFilterBolt())
            .name("EventFilterBolt")
            // project the event, (ad-id, ad-ts)
            .map(new EventMapper())
            .name("Project")
            // perform join with redis data, (campaign-id, ad-id, ad-ts)
            .flatMap(new RedisJoinBolt())
            .name("RedisJoinBolt")
            // process campaign, keyBy campaign id
            .keyBy(tuple -> tuple.f0)
            .window(TumblingEventTimeWindows.of(Time.seconds(10)))
            .process(new CampaignProcessor())
            .name("CampaignProcessor")
            .sinkTo(sink)
            .name("KafkaSink-" + RandomStringUtils.random(10, true, true));

        env.execute(jobName);
    }
}
