package de.tu_berlin.dos.arm.wordcount.processor;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import de.tu_berlin.dos.arm.wordcount.common.utils.FileReader;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Demonstrates, using the high-level KStream DSL, how to implement the WordCount program
 * that computes a simple word occurrence histogram from an input text.
 * <p>
 * In this example, the input stream reads from a topic named "streams-plaintext-input", where the values of messages
 * represent lines of text; and the histogram output is written to topic "streams-wordcount-output" where each record
 * is an updated count of a single word.
 * <p>
 * Before running this example you must create the input topic and the output topic (e.g. via
 * {@code bin/kafka-topics.sh --create ...}), and write some data to the input topic (e.g. via
 * {@code bin/kafka-console-producer.sh}). Otherwise you won't see any data arriving in the output topic.
 */
public final class WordCount {

    private static final Logger LOG = Logger.getLogger(WordCount.class);

    public static void main(final String[] args) {

        String appId = "kstreams-wordcount";
        if (args[0].length() > 0) {
            appId = args[0];
        }

        // properties
        final Properties props = new Properties();
        // TODO: app id config has to come from argument and differ for each approach
        props.putIfAbsent(StreamsConfig.APPLICATION_ID_CONFIG, appId);
        props.putIfAbsent(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.putIfAbsent(StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG, "DEBUG");
        props.putIfAbsent(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.putIfAbsent(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.putIfAbsent(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, "exactly_once_v2");
        props.putIfAbsent(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10000);  // 10s checkpoint interval
        props.putIfAbsent(StreamsConfig.PROBING_REBALANCE_INTERVAL_MS_CONFIG, 180000);  // 3 mins from default 10 mins
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        final StreamsBuilder builder = new StreamsBuilder();

        final KStream<String, String> source = builder.stream("word-events");

        final KTable<String, Long> counts = source
                .flatMapValues(value -> Arrays.asList(value.toLowerCase(Locale.getDefault()).split("\\W+")))
                .groupBy((key, value) -> value)
                .count();

        // need to override value serde to Long type
        counts.toStream().foreach((word, count) -> LOG.info("word: " + word + " -> " + count));
                //.to("wordcount-output", Produced.with(Serdes.String(), Serdes.Long()));
        final KafkaStreams streams = new KafkaStreams(builder.build(), props);
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-wordcount-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (final Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}