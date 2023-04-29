package de.tu_berlin.dos.arm.traffic_monitoring.producer;

import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status.Failure;
import de.tu_berlin.dos.arm.traffic_monitoring.common.events.Point;
import de.tu_berlin.dos.arm.traffic_monitoring.common.events.TrafficEvent;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Logger;
//import org.joda.time.Instant;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class Vehicles {

    static final Logger LOG = Logger.getLogger(Run.class);
    static final ActorSystem SYSTEM = ActorSystem.create("vehicle-system");

    private static String genRandomLP() {

        int number = new Random().nextInt(9999);
        String [] letters = new String[]{"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"};

        StringBuilder sb = new StringBuilder(letters[new Random().nextInt(26 - 1)]);

        if (new Random().nextInt(100) > 50) sb.append(letters[new Random().nextInt(26 - 1)]);
        if (new Random().nextInt(100) > 50) sb.append(letters[new Random().nextInt(26 - 1)]);
        sb.append('-').append(letters[new Random().nextInt(26 - 1)]);
        if (new Random().nextInt(100) > 50) sb.append(letters[new Random().nextInt(26 - 1)]);
        sb.append(' ').append(number);
        if (new Random().nextInt(10000) < 126) sb.append('E');

        return sb.toString();
    }

    public static class VehicleActor extends AbstractActor {

        static final Logger LOG = Logger.getLogger(VehicleActor.class);
        static final AtomicInteger VEHICLE_COUNT = new AtomicInteger(0);
        static final Random RAND = new Random();

        private final int updateInterval;
        private final List<Point> waypoints;

        static Props props(int updateInterval, List<Point> waypoints) {

            return Props.create(VehicleActor.class, updateInterval, waypoints);
        }

        static final class Emit {

            String topic;
            KafkaProducer<String, TrafficEvent> kafkaProducer;

            Emit(String topic, KafkaProducer<String, TrafficEvent> kafkaProducer) {

                this.topic = topic;
                this.kafkaProducer = kafkaProducer;
            }
        }

        public VehicleActor(int updateInterval, List<Point> waypoints) {

            this.updateInterval = updateInterval;
            this.waypoints = waypoints;
        }

        @Override
        public void preStart() {
            VEHICLE_COUNT.getAndIncrement();
        }

        @Override
        public void postStop() {
            VEHICLE_COUNT.getAndDecrement();
        }

        private List<TrafficEvent> generateTrafficEvents() {
            // generate static vehicle event values
            String vehicleId = genRandomLP();
            // mark initial timestamp and instantiate list for all vehicle events
            //Date timestamp = new Date();
            long timestamp = Instant.now().getEpochSecond() * 1000;
            List<TrafficEvent> trafficEvents = new ArrayList<>();
            // loop through waypoints in pairs to process road section
            for (int i = 0; i < this.waypoints.size() - 1; ++i) {
                // get waypoint pairs
                Point p1 = this.waypoints.get(i);
                Point p2 = this.waypoints.get(i + 1);
                // Use Geodesic Inverse function to find distance in meters and angle between waypoints
                GeodesicData g1 = Geodesic.WGS84.Inverse(p1.lt, p1.lg, p2.lt, p2.lg);
                // generate random average speed for road section
                double avgSpeed = RAND.nextInt(80 - 10) + 10;
                // set distance to zero for start of road section and loop until distance is reached
                double currDistance = 0.0;
                while (currDistance < g1.s12) {
                    // Use Geodesic Direct function to determine current lat and long coordinates
                    GeodesicData g2 = Geodesic.WGS84.Direct(g1.lat1, g1.lon1, g1.azi1, currDistance);
                    // create vehicle event and add to vehicle event list
                    trafficEvents.add(new TrafficEvent(vehicleId, new Point((float) g2.lat2, (float) g2.lon2), timestamp));
                    // increment timestamp by time interval for next event
                    //timestamp = Date.from(timestamp.toInstant().plusMillis(this.updateInterval));
                    timestamp = timestamp + this.updateInterval;
                    // calculate new distance based on average speed (in meters per second) and update interval (in milliseconds)
                    currDistance += (avgSpeed / 3.6) * (this.updateInterval / 1000f);
                    //currDistance += (avgSpeed / 3.6) * this.updateInterval;
                }
            }
            return trafficEvents;
        }

        @Override
        public Receive createReceive() {

            return receiveBuilder()
                .match(Emit.class, e -> {
                    try {
                        // generate list of vehicle events
                        List<TrafficEvent> events = this.generateTrafficEvents();
                        // loop through events and emit them to kafka
                        for (int i = 0; i < events.size(); ++i) {
                            TrafficEvent event = events.get(i);
                            // determine how long we need to wait to emit this event
                            long wait = event.getTs() - System.currentTimeMillis();
                            // overdue, so emit event to kafka
                            if (wait <= 0) {
                                e.kafkaProducer.send(
                                    new ProducerRecord<>(
                                        //e.topic, null, event.getTs().getTime(), event.getLp(), event));
                                        e.topic, null, event.getTs(), event.getLp(), event));
                            }
                            // ensure actor stays alive until after all last message has been sent
                            else if (i == events.size() - 1) {
                                SYSTEM.scheduler()
                                    .scheduleOnce(Duration.ofMillis(wait), () -> {
                                        e.kafkaProducer.send(
                                            new ProducerRecord<>(
                                                //e.topic, null, event.getTs().getTime(), event.getLp(), event));
                                                e.topic, null, event.getTs(), event.getLp(), event));
                                        context().stop(self());
                                    }, SYSTEM.dispatcher());
                            }
                            // if event is not overdue, schedule it to be sent at the appropriate time
                            else {
                                SYSTEM.scheduler()
                                    .scheduleOnce(Duration.ofMillis(wait), () -> {
                                        e.kafkaProducer.send(
                                            new ProducerRecord<>(
                                                //e.topic, null, event.getTs().getTime(), event.getLp(), event));
                                                e.topic, null, event.getTs(), event.getLp(), event));
                                    }, SYSTEM.dispatcher());
                            }
                        }
                    }
                    catch (Exception ex) {
                        getSender().tell(new Failure(ex), getSelf());
                    }
                })
                .matchAny(o -> LOG.error("received unknown message: " + o))
                .build();
        }
    }
}
