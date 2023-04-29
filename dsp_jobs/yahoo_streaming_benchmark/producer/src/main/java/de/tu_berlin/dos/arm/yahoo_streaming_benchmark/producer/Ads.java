package de.tu_berlin.dos.arm.yahoo_streaming_benchmark.producer;

import akka.actor.AbstractActor;
import akka.actor.Props;
import de.tu_berlin.dos.arm.yahoo_streaming_benchmark.common.ads.AdEvent;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Ads {

    // for random event generation
    private static Random   rand = new Random();
    private static final String[] eventTypes = {"view", "click", "purchase"};
    public static final String[] campaignIds = makeIds(100);
    public static final String[] adIds = makeIds(10 * 100); // 10 ads per campaign

    // returns an array of n uuids
    public static String[] makeIds(int n) {
        String[] uuids = new String[n];
        for (int i = 0; i < n; i++)
            uuids[i] = RandomStringUtils.randomAlphanumeric(16);
        return uuids;
    }

    static final Logger LOG = Logger.getLogger(Ads.class);

    public static class AdActor extends AbstractActor {

        public static AtomicInteger counter = new AtomicInteger(0);

        static Props props(int number, String topic, KafkaProducer<String, AdEvent> kafkaProducer) {

            return Props.create(AdActor.class, number, topic, kafkaProducer);
        }

        static final class Emit {

            int eventsCount;
            long creationDate;

            Emit(int eventsCount, long creationDate) {
                this.eventsCount = eventsCount;
                this.creationDate = creationDate;
            }
        }

        private String topic;
        private KafkaProducer<String, AdEvent> kafkaProducer;
        private int number;

        public AdActor(int number, String topic, KafkaProducer<String, AdEvent> kafkaProducer) {
            this.topic = topic;
            this.kafkaProducer = kafkaProducer;
            this.number = number;
        }

        @Override
        public Receive createReceive() {

            return receiveBuilder()
                .match(Emit.class, e -> {

                    if (this.number <= e.eventsCount) {
                        counter.incrementAndGet();
                        String adId   = adIds[rand.nextInt(adIds.length)];
                        String eventType = eventTypes[rand.nextInt(eventTypes.length)];
                        
                        AdEvent adEvent = new AdEvent(e.creationDate, adId, eventType);
                        this.kafkaProducer.send(new ProducerRecord<>(
                                this.topic, null, adEvent.getTs(), adEvent.getId(), adEvent));
                    }
                })
                .matchAny(o -> LOG.error("received unknown message: " + o))
                .build();
        }
    }

}
