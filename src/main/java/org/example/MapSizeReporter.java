package org.example;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.monitor.LocalMapStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MapSizeReporter implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MapSizeReporter.class);
    private static final String FORMAT = "%,8d";

    private final HazelcastInstance hz;
    private final ScheduledExecutorService executorService;

    public MapSizeReporter(HazelcastInstance hz) {
        this.hz = hz;
        executorService = Executors.newScheduledThreadPool(1);
    }

    public void start(int interval) {
        executorService.scheduleAtFixedRate(this, interval, interval, TimeUnit.SECONDS);
    }

    public void stop() {
        executorService.shutdown();
    }

    @Override
    public void run() {
        Map<String, MapConfig> mapConfigs = hz.getConfig().getMapConfigs();
        mapConfigs.keySet().stream().map(hz::getMap).forEach(map -> {
            LocalMapStats stats = map.getLocalMapStats();
            log.info("{} -> {}.ownedEntryCost={} {}.backupEntryMemoryCost={} r={}",
                    hz.getName(),
                    map.getName(), String.format(FORMAT, stats.getOwnedEntryMemoryCost()),
                    map.getName(), String.format(FORMAT, stats.getBackupEntryMemoryCost()),
                    ((float)stats.getBackupEntryMemoryCost() / stats.getOwnedEntryMemoryCost())
            );
        });
    }

}
