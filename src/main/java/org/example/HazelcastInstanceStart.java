package org.example;

import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import java.io.FileNotFoundException;
import java.net.URL;

/**
 * Simple main method for starting a single hazelcast instance with an attached map size reporting thread (for manual testing).
 * Runs until manually killed.
 */
public class HazelcastInstanceStart {
    public static final int REPORTING_INTERVAL_SECONDS = 5;

    public static void main(String[] args) throws FileNotFoundException {
        URL cfg = HazelcastInstanceStart.class.getResource("/hazelcast.xml");
        HazelcastInstance hz = Hazelcast.newHazelcastInstance(new FileSystemXmlConfig(cfg.getFile()));

        MapSizeReporter reporter = new MapSizeReporter(hz);
        reporter.start(REPORTING_INTERVAL_SECONDS);

        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
