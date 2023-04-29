package de.tu_berlin.dos.arm.yahoo_streaming_benchmark.producer;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.routing.*;
import de.tu_berlin.dos.arm.yahoo_streaming_benchmark.common.data.TimeSeries;
import de.tu_berlin.dos.arm.yahoo_streaming_benchmark.common.ads.AdEvent;
import de.tu_berlin.dos.arm.yahoo_streaming_benchmark.producer.Generator.GeneratorActor.Broadcast;
import de.tu_berlin.dos.arm.yahoo_streaming_benchmark.producer.Ads.AdActor;
import de.tu_berlin.dos.arm.yahoo_streaming_benchmark.producer.Ads.AdActor.Emit;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public enum Generator { GET;

    private static final Logger LOG = Logger.getLogger(Generator.class);
    static final ActorSystem SYSTEM = ActorSystem.create("ads-system");
    private final StopWatch stopWatch = new StopWatch();

    static class GeneratorActor extends AbstractActor {

        static Props props(int largest, String topic, KafkaProducer<String, AdEvent> kafkaProducer) {
            return Props.create(GeneratorActor.class, largest, topic, kafkaProducer);
        }

        static final class Broadcast {
            public final int eventsCount;

            public Broadcast(int eventsCount) {
                this.eventsCount = eventsCount;
            }

        }

        private int largest;
        private Router router;
        private String topic;
        private KafkaProducer<String, AdEvent> kafkaProducer;

        private GeneratorActor(int largest, String topic, KafkaProducer<String, AdEvent> kafkaProducer) {

            this.largest = largest;
            this.topic = topic;
            this.kafkaProducer = kafkaProducer;

            List<Routee> routees = new ArrayList<>();
            for (int i = 1; i <= largest; i++) {

                ActorRef poster = getContext().actorOf(AdActor.props(i, topic, kafkaProducer));
                getContext().watch(poster);
                routees.add(new ActorRefRoutee(poster));
            }
            this.router = new Router(new BroadcastRoutingLogic(), routees);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                .match(Broadcast.class, e -> {
                    router.route(new Emit(e.eventsCount, Instant.now().getEpochSecond()), getSender());
                })
                .matchAny(o -> LOG.error("received unknown message: " + o))
                .build();
        }
    }

    public void generate(TimeSeries ts, int largest, String topic, KafkaProducer<String, AdEvent> kafkaProducer) {

        ActorRef generatorActor = SYSTEM.actorOf(GeneratorActor.props(largest, topic, kafkaProducer));
        Generator.GET.stopWatch.start();

        int current = (int) Generator.GET.stopWatch.getTime(TimeUnit.SECONDS);
        for (int i = 0; i < ts.observations.size(); i++) {

            generatorActor.tell(new Broadcast(ts.observations.get(i).value), ActorRef.noSender());

            while (current < i) {
                current = (int) Generator.GET.stopWatch.getTime(TimeUnit.SECONDS);
            }
        }
    }
}
