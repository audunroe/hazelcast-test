package org.example;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

public class HazelcastMemCostTest {

    private static final Logger log = LoggerFactory.getLogger(HazelcastMemCostTest.class);
    public static final String ASYNC_BACKUP_MAP = "asyncBackupMap";

    private static FileSystemXmlConfig config;

    @BeforeClass
    public static void beforeClass() throws FileNotFoundException {
        config = new FileSystemXmlConfig(HazelcastMemCostTest.class.getResource("/hazelcast.xml").getFile());
    }

    /*
     * For testing using manually started nodes (HazelcastInstanceStart). This test just populates the map using
     * hazelcast-client.
     */
    @Ignore
    @Test
    public void manualTesting() throws Exception {

        HazelcastInstance client = HazelcastClient.newHazelcastClient();

        int numEntries = 10_000;
        log.info("Adding {} entries to map.", numEntries);

        IMap<String, String> map = client.getMap(ASYNC_BACKUP_MAP);
        for (int i = 0; i< numEntries; i++) {
            map.put(randomAlphanumeric(20), randomAlphanumeric(2000));
        }
    }

    /*
         * Tests the whole scenario within a single JVM, starting X amount of instances, adding Y amount of entries, then
         * reporting the observed memory costs from the first instance's LocalMapStats.
         */
    @Test
    public void testMemoryCosts() throws Exception {
        final int numInstances = 4;
        final int numEntries = 10_000;
        log.info ("#### Starting {} hazelcast instances", numInstances);
        // Start instance then wait until each instance sees at least 'numInstances' members including itself (up to fjp.size members).
        // before proceeding. Not really necessary but makes it easier to look at stats if numbers don't fluctuate too much initially due to repartitioning.
        final ForkJoinPool fjp = new ForkJoinPool(8);
        List<CompletableFuture<HazelcastInstance>> futures = IntStream.range(0, numInstances)
                .mapToObj(n -> CompletableFuture.supplyAsync(() -> {
                    log.debug("starting instance #{}", n);
                    HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
                    int currentSize=0;
                    for (int retries=0;retries<120;retries++) {
                        currentSize = instance.getCluster().getMembers().size();
                        if (currentSize >= Math.min(fjp.getPoolSize(), numInstances)) {
                            return instance;
                        } else {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) { //
                            }
                        }
                    }
                    throw new RuntimeException("Gave up waiting for cluster size to reach minimum expected size ("+ currentSize +")");
                }, fjp)).collect(Collectors.toList());

        List<HazelcastInstance> hazelcastInstances = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        HazelcastInstance hz0 = hazelcastInstances.get(0);
        MapSizeReporter reporter = new MapSizeReporter(hz0); // only attaching cost reporter to one of the nodes only
        reporter.start(HazelcastInstanceStart.REPORTING_INTERVAL_SECONDS);

        log.info("#### Begin adding {} entries to map", numEntries);
        IMap<String, String> map0 = hz0.getMap(ASYNC_BACKUP_MAP);
        for (int i = 0; i < numEntries; i++) {
            map0.put(randomAlphanumeric(20), randomAlphanumeric(2000));
        }
        log.info("#### Done adding entries. Sleeping for a bit.");

        // keep alive for a bit to see how things develop with an idle cluster.
        Thread.sleep(60000);
        log.info("#### Done sleeping.");
    }

}
