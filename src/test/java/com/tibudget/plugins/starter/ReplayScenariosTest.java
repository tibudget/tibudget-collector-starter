package com.tibudget.plugins.starter;

import com.tibudget.test.CollectorResultJson;
import com.tibudget.test.OkHttpInternetProvider;
import com.tibudget.test.PDFToolsProviderPDFBox;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replay HTTP scenarios recorded with {@link TibuSimulator} and compare the collect result with the saved {@code collect.json}.
 * The recorded responses replace live website access, so these tests can run without the demo website.
 * <p>
 * To add a scenario, record a collect with {@link TibuSimulator}, then add a test using its scenario directory.
 * Use the same configuration values and initial settings, accounts, and recurring payments as in the recorded run.
 * If the scenario requires an OTP code, provide it through the OTP provider passed when initializing the collector.
 * <p>
 * Review differences before replacing an expected result. Update it only when the new behavior is intended.
 */
class ReplayScenariosTest {

    /**
     * Check a complete collect without OTP or previously saved state against the recorded result.
     */
    @Test
    void scenario1() throws Exception {
        OkHttpInternetProvider offLineInternetProvider = new OkHttpInternetProvider()
                .replayDir("src/test/resources/scenarios/2026-10-27_15-20-17_801")
                ;

        // 1) Create the collector with the configuration used for the recording.
        StarterCollector collector = new StarterCollector();
        collector.setMinIntervalBetweenRequestInMs(0); // Request delays are not needed when replaying recorded responses.
        collector.setLogin("camille@example.test");
        collector.setPassword("demo1234");

        // 2) Use recorded responses and start without saved settings, accounts, or recurring payments.
        collector.init(offLineInternetProvider, null, null, new PDFToolsProviderPDFBox(), null, null, null);

        // 3) Check that the configuration is valid before running the collect.
        assertEquals(0, collector.validate().size());

        // 4) Check that the collect finishes.
        assertTrue(collector.collect());

        // 5) Compare the collected data with the expected result saved in the scenario directory.
        CollectorResultJson.assertMatches(new File(offLineInternetProvider.getReplayDir(), "collect.json"), collector);
    }
}
