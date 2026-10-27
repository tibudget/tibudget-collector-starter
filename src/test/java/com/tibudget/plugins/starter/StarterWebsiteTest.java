package com.tibudget.plugins.starter;

import com.tibudget.api.InternetProvider;
import com.tibudget.api.OTPProvider;
import com.tibudget.api.exceptions.AccessDeny;
import com.tibudget.api.exceptions.CollectError;
import com.tibudget.dto.AccountDto;
import com.tibudget.dto.TransactionDto;
import com.tibudget.utils.exception.MustStopException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Check how {@link StarterCollector} reads the demo website using HTML files from {@code src/test/resources/site}.
 * The real sign-in, parsing, and progress code runs with a simulated {@link InternetProvider}; no website is contacted.
 * <p>
 * When adapting the starter, replace the HTML files, routes, and expected values with examples from your website.
 * Keep focused cases for authentication, saved state, and failures that a successful replay does not cover.
 * Use {@link ReplayScenariosTest} to compare a complete recorded result and {@link StarterCollectorTest} for collect flow rules.
 */
class StarterWebsiteTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * Check representative field values from the demo HTML, including item quantities, transaction states,
     * and loyalty metadata. Orders and subscription invoices must be merged from oldest to newest.
     */
    @Test
    @DisplayName("Read purchases, subscriptions, and attachments from HTML")
    void collectsOrdersAndSubscriptionInvoicesInChronologicalOrder() throws Exception {
        WebsiteProvider provider = new WebsiteProvider();
        StarterCollector collector = collector(provider);

        assertTrue(collector.collect());
        assertEquals(2, collector.getAccounts().size());
        assertEquals(42, collector.getTransactions().size());
        for (int index = 1; index < collector.getTransactions().size(); index++) {
            assertFalse(collector.getTransactions().get(index).getDateTransaction().before(collector.getTransactions().get(index - 1).getDateTransaction()));
        }

        // Check purchase amounts, item prices, quantities, and available documents.
        TransactionDto order = transaction(collector, "TB-1002");
        assertEquals(-51.0, order.getAmount());
        assertEquals(2, order.getItems().size());
        assertEquals(39.0, order.getItems().get(0).getPrice());
        assertEquals(1, order.getFiles().size());
        assertEquals(1, order.getItems().get(0).getFiles().size());
        assertEquals(36.0, transaction(collector, "TB-1001").getItems().get(0).getPrice());
        assertEquals(2, transaction(collector, "TB-1001").getItems().get(0).getQuantity());

        // Website states and missing document links must be reflected in the result.
        assertEquals(TransactionDto.TransactionDtoState.PENDING, transaction(collector, "TB-1006").getState());
        assertEquals(TransactionDto.TransactionDtoState.DELETED, transaction(collector, "TB-1003").getState());
        assertTrue(transaction(collector, "TB-1004").getFiles().isEmpty());
        assertTrue(transaction(collector, "TB-1005").getItems().get(0).getFiles().isEmpty());

        // Subscription invoices share the purchase history; loyalty points belong to a separate account.
        assertEquals(12, collector.getTransactions().stream().filter(transaction -> transaction.getRecurrentPaymentUuid() != null).count());
        assertEquals(-9.9, collector.getRecurringPayments().get(0).getExpectedAmount());
        assertEquals("MD-004281", collector.accounts.get("MD-004281").getMetadata(AccountDto.METADATA_LOYALTY_CARD_REFERENCE));

        // Finish downloading the current page before requesting the next one.
        int invoiceRequest = provider.requests.indexOf("download:/fr/factures/TB-977.pdf");
        int nextPageRequest = provider.requests.indexOf("get:/fr/compte?page=2");
        assertTrue(invoiceRequest >= 0, "The invoice from the current page must be downloaded.");
        assertTrue(nextPageRequest > invoiceRequest, "Read the next page only after collecting the current page.");
    }

    /**
     * Pass saved settings, accounts, and recurring payments to a new collector, as Tibu does between runs.
     * Already collected purchases must be skipped. UUIDs stay unchanged while website labels are refreshed.
     */
    @Test
    @DisplayName("Resume with existing identities and refreshed website labels")
    void resumesWithoutDuplicatesAndPreservesIdentitiesWhileUpdatingLabels() throws Exception {
        StarterCollector first = collector(new WebsiteProvider());
        assertTrue(first.collect());
        String uuid = first.accounts.get("camille@example.test").getUuid();
        first.accounts.get("camille@example.test").setLabel("My purchases");
        String recurringUuid = first.getRecurringPayments().get(0).getUuid();
        first.getRecurringPayments().get(0).setLabel("My subscription");

        WebsiteProvider resumedProvider = new WebsiteProvider();
        StarterCollector second = collector(resumedProvider);
        second.init(resumedProvider, null, null, null, first.getSettings(), first.getAccounts(), first.getRecurringPayments());

        assertTrue(second.collect());
        assertTrue(second.getTransactions().isEmpty());
        assertEquals(uuid, second.accounts.get("camille@example.test").getUuid());
        assertEquals("Maison démo", second.accounts.get("camille@example.test").getLabel());
        assertEquals(recurringUuid, second.getRecurringPayments().get(0).getUuid());
        assertEquals("Maison Plus", second.getRecurringPayments().get(0).getLabel());
    }

    /**
     * Check the OTP channel and code format, then submit the code with the CSRF token from the HTML form.
     * Disable shopping sync to keep this scenario focused on authentication.
     */
    @Test
    @DisplayName("Submit the SMS code and preserve the form CSRF token")
    void supportsOtpAndPreservesCsrf() throws Exception {
        WebsiteProvider provider = new WebsiteProvider();
        StarterCollector collector = collector(provider);
        collector.withOtp = true;
        collector.otpProvider = (channel, keyword, pattern, prompt) -> {
            assertEquals(OTPProvider.Channel.SMS, channel);
            assertEquals(OTPProvider.PATTERN_4_DIGIT, pattern);
            return "1234";
        };
        collector.accounts.put("camille@example.test", disabledShoppingAccount());

        assertTrue(collector.collect());
        assertTrue(provider.requests.contains("post:/fr/otp"));
        assertTrue(provider.requests.stream().noneMatch(request -> request.startsWith("download:")));
    }

    /**
     * A refused sign-in must raise an access error before collecting any data or saving progress.
     */
    @Test
    @DisplayName("Report refused credentials without saving progress")
    void rejectsInvalidCredentials() {
        WebsiteProvider provider = new WebsiteProvider();
        provider.rejectLogin = true;
        StarterCollector collector = collector(provider);

        assertThrows(AccessDeny.class, collector::collect);
        assertTrue(collector.getSettings().isEmpty());
        assertTrue(collector.getTransactions().isEmpty());
        assertTrue(provider.requests.stream().noneMatch(request -> request.startsWith("download:")));
    }

    /**
     * The shared collector base allows one transaction before honoring a stop request.
     * Check that real HTML parsing still produces accounts, a subscription, and saved progress before stopping.
     */
    @Test
    @DisplayName("Save one transaction when a stop was requested before the collect")
    void stoppedCollectorSynchronizesAccountsAndCollectsOneTransaction() throws Exception {
        WebsiteProvider provider = new WebsiteProvider();
        StarterCollector collector = collector(provider);
        collector.stop();

        assertFalse(collector.collect());
        assertEquals(2, collector.getAccounts().size());
        assertEquals(1, collector.getRecurringPayments().size());
        assertEquals(1, collector.getTransactions().size());
        assertFalse(collector.getSettings().isEmpty());
    }

    /**
     * A failed attachment download must leave the transaction incomplete and its progress unsaved.
     * This allows the next run to retry it instead of silently losing the document.
     */
    @Test
    @DisplayName("Retry a transaction after its attachment download fails")
    void failedDownloadDoesNotCheckpointTheTransaction() {
        WebsiteProvider provider = new WebsiteProvider();
        provider.failDownloads = true;
        StarterCollector collector = collector(provider);

        assertThrows(CollectError.class, collector::collect);
        assertTrue(collector.getTransactions().isEmpty());
        assertTrue(collector.getSettings().isEmpty());
    }

    /**
     * Create a fresh demo configuration. Skip request delays while preserving the real stop condition.
     */
    private StarterCollector collector(WebsiteProvider provider) {
        StarterCollector collector = new StarterCollector() {
            @Override
            public void waitForNextRequest() {
                if (mustStop()) {
                    throw new MustStopException();
                }
            }
        };
        collector.login = "camille@example.test";
        collector.password = "demo1234";
        collector.init(provider, null, null, null, Map.of(), List.of(), List.of());
        return collector;
    }

    private AccountDto disabledShoppingAccount() {
        AccountDto account = new AccountDto();
        account.setId("camille@example.test");
        account.setUuid("existing-uuid");
        account.setType(AccountDto.AccountDtoType.SHOPPING);
        account.setSyncEnabled(false);
        return account;
    }

    private TransactionDto transaction(StarterCollector collector, String id) {
        return collector.getTransactions().stream().filter(transaction -> transaction.getId().equals(id)).findFirst().orElseThrow();
    }

    /**
     * Serve the demo HTML according to the requested route and simulated sign-in state.
     * Test flags select failures; requests are recorded so tests can check their order.
     * Downloads create small temporary files for DTO attachment checks, not valid PDF or image documents.
     */
    private class WebsiteProvider implements InternetProvider {
        final List<String> requests = new ArrayList<>();
        boolean authenticated;
        boolean rejectLogin;
        boolean failDownloads;
        int fileNumber;

        @Override
        public Response get(String url, Map<String, String> headers) throws IOException {
            String path = java.net.URI.create(url).getRawPath();
            String query = java.net.URI.create(url).getRawQuery();
            requests.add("get:" + path + (query == null ? "" : "?" + query));
            if (!authenticated) {
                return html("login.html", "/fr/connexion");
            }
            if (path.endsWith("/abonnement/factures")) {
                return html("subscription.html", path);
            }
            String page = query == null ? "1" : query.substring("page=".length());
            return html("account-" + page + ".html", path);
        }

        @Override
        public Response post(String url, String body, String contentType, Map<String, String> headers) throws IOException {
            String path = java.net.URI.create(url).getPath();
            requests.add("post:" + path);
            assertTrue(body.contains("csrf=test-csrf"), "Submit the CSRF token from the form with every sign-in or OTP request.");
            if (rejectLogin) {
                return html("login.html", "/fr/connexion");
            }
            if (body.contains("with_otp=1")) {
                return html("otp.html", "/fr/otp");
            }
            if (path.endsWith("/otp")) {
                assertTrue(body.contains("otp=1234"));
            }
            authenticated = true;
            return html("account-1.html", "/fr/compte");
        }

        @Override
        public Response downloadFile(String url, Map<String, String> headers, String forceContentType) throws IOException {
            requests.add("download:" + java.net.URI.create(url).getPath());
            if (failDownloads) {
                throw new IOException("Download failed");
            }
            Path file = temporaryDirectory.resolve("attachment-" + fileNumber++);
            byte[] content = "application/pdf".equals(forceContentType) ? "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII) : new byte[]{(byte) 255, (byte) 216, (byte) 255};
            Files.write(file, content);
            return new Response(200, "OK", "GET", url, "HTTP/1.1", file.toString(), Map.of());
        }

        private Response html(String fixture, String location) throws IOException {
            try (var input = getClass().getResourceAsStream("/site/" + fixture)) {
                if (input == null) {
                    throw new IOException("Missing test HTML file: /site/" + fixture);
                }
                return new Response(200, "OK", "GET", location, "HTTP/1.1", new String(input.readAllBytes(), StandardCharsets.UTF_8), Map.of());
            }
        }
    }
}
