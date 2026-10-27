package com.tibudget.plugins.starter;

import com.tibudget.dto.AccountDto;
import com.tibudget.dto.MessageDto;
import com.tibudget.dto.RecurringPaymentDto;
import com.tibudget.dto.TransactionDto;
import com.tibudget.test.*;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.logging.LogManager;

/**
 * Run {@link StarterCollector} locally with the providers used to simulate the Tibu mobile app.
 * Set the demo credentials below before running {@link #main(String[])} from your IDE.
 * OTP codes, when needed, are entered in the console.
 * <p>
 * Cookies, downloaded files, and collect progress are stored under {@code target/}.
 * Only settings are restored between runs; existing accounts and recurring payments are not restored.
 * Delete {@code target/settings.txt} to collect the full history again, and {@code target/cookies.txt}
 * to test a new sign-in.
 * <p>
 * HTTP requests and responses are recorded under {@code src/test/resources/scenarios}.
 * The result is saved as {@code collect.json} in the recorded scenario directory.
 * Use the recording with {@link ReplayScenariosTest} to check future changes against the saved result.
 * HTML pages are also saved under {@code target/scenarios-html} to help inspect the website.
 * <p>
 * Use demo accounts for recordings. Check recorded files for credentials, cookies, and personal data before committing them.
 */
public class TibuSimulator {

    /**
     * Validate the configuration, run the collect, and save and display its result.
     * Validation messages are printed but do not prevent the collect from running.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {

        try {
            LogManager.getLogManager().readConfiguration(TibuSimulator.class.getResourceAsStream("/logging.properties"));

            StringMapSerializer settingsSerializer = new StringMapSerializer("target/settings.txt");
            OkHttpInternetProvider onLineInternetProvider = new OkHttpInternetProvider()
                    .cookiesFile("target/cookies.txt")
                    .downloadDir("target/downloads")
                    .harDir("src/test/resources/scenarios")
                    .htmlDir("target/scenarios-html")
                    .rateLimiter(20)
                    ;

            // 1) Create the collector and set the credentials for the account to test.
            StarterCollector collector = new StarterCollector();
            collector.setLogin("camille@example.test");
            collector.setPassword("demo1234");

            // 2) Provide network access, console OTP input, PDF tools, and saved settings.
            collector.init(onLineInternetProvider, null, new OTPProviderConsole(), new PDFToolsProviderPDFBox(), settingsSerializer.load(), null, null);

            // 3) Display configuration validation messages.
            Collection<MessageDto> messages = collector.validate();
            for (MessageDto message : messages) {
                System.out.println(message);
            }

            // 4) Run the collect. A false result means that more work remains.
            collector.collect();

            // 5) Save progress so the next run can skip transactions already collected.
            settingsSerializer.save(collector.getSettings());

            // Save the expected result alongside the HTTP recording for replay tests.
            CollectorResultJson.save(new File(onLineInternetProvider.getHarDir(), "collect.json"), collector);

            // Display the collected data and settings for inspection.
            System.out.println("\nCollect for account: " + collector.getConfigurationName() + " | hash = " + collector.getConfigurationIdHash());

            System.out.println("-- " + collector.getAccounts().size() + " accounts found --");
            for (AccountDto account : collector.getAccounts()) {
                System.out.println(account);
            }

            System.out.println("-- " + collector.getTransactions().size() + " transactions found --");
            for (TransactionDto transaction : collector.getTransactions()) {
                System.out.println(transaction);
            }

            System.out.println("-- " + collector.getRecurringPayments().size() + " recurring payments found --");
            for (RecurringPaymentDto recurringPaymentDto : collector.getRecurringPayments()) {
                System.out.println(recurringPaymentDto);
            }

            System.out.println("-- " + collector.getSettings().size() + " settings found --");
            for (Map.Entry<String, String> setting : collector.getSettings().entrySet()) {
                System.out.println(setting.getKey() + " = " + setting.getValue());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
