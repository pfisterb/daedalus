package de.tu_berlin.dos.arm.traffic_monitoring.processor;

import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class VehicleSegmentCache {

    private static final Logger LOG = Logger.getLogger(VehicleSegmentCache.class);

    private static class LimitedLinkedHashMap extends LinkedHashMap<String, String> {

        private static final int MAX_ENTRIES = 19;

        @Override
        protected boolean removeEldestEntry(Entry<String, String> entry) {

            LOG.info("Size: " + size());
            return size() > MAX_ENTRIES;
        }
    }

    public static class Singleton {

        private static Singleton self = null;

        private LimitedLinkedHashMap cache = null;
        private Map<String, String> syncCache = null;

        private Singleton() {

            this.cache = new LimitedLinkedHashMap();
            this.syncCache = Collections.synchronizedMap(cache);
        }

        public static synchronized Singleton getInstance() {

            if (self == null) self = new Singleton();
            return self;
        }
    }

    public static boolean containsKey(String key) {

        return Singleton.getInstance().cache.containsKey(key);
    }

    public static String get(String key) {

        return Singleton.getInstance().cache.get(key);
    }

    public static String put(String key, String value) {

        return Singleton.getInstance().syncCache.put(key, value);
    }
}
