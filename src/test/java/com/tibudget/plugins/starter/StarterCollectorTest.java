package com.tibudget.plugins.starter;

import com.tibudget.api.CollectorPlugin;
import com.tibudget.api.exceptions.CollectError;
import com.tibudget.dto.AccountDto;
import com.tibudget.dto.TransactionDto;
import com.tibudget.utils.CollectorPendingTransaction;
import com.tibudget.utils.CollectorProgress;
import com.tibudget.utils.CollectorTransactionPage;
import com.tibudget.utils.exception.MustStopException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Check the collect flow independently of the demo website: pagination, progress, stop requests, and disabled accounts.
 * {@link TestCollector} supplies history pages and transaction details in memory while keeping the real
 * {@link StarterCollector#collect()} implementation.
 * <p>
 * Adapt these tests when changing the collect flow. Use {@link ReplayScenariosTest} for recorded website responses
 * and expected collect results. These tests do not check sign-in, HTML parsing, downloads, or complete DTO field rules.
 */
class StarterCollectorTest {

    /**
     * The shared collector base allows one transaction before honoring a stop request.
     * This keeps a short run useful and leaves progress for the next run.
     */
    @Test
    @DisplayName("A stop request before the collect still allows one transaction")
    void stopBeforeCollectStillAllowsOneTransaction() throws Exception {
        TestCollector collector = new TestCollector();
        collector.stop();
        collector.pages.add(new CollectorTransactionPage(List.of(reference("a", "2026-01-02")), "page-2"));

        assertFalse(collector.collect());
        assertEquals(List.of("signIn", "accounts", "recurringPayments", "page:null", "a"), collector.steps);
        assertEquals(1, collector.pagesRead);
        assertEquals(Set.of("a"), collector.savedProgress().getTransactionReferences());
    }

    /**
     * Process the current page before requesting the next one, using its page token.
     * Saving a newer date must replace references saved for the previous date.
     */
    @Test
    @DisplayName("Finish each history page before reading the next page")
    void collectsEachPageBeforeRequestingTheNextOne() throws Exception {
        TestCollector collector = new TestCollector();
        collector.pages.add(new CollectorTransactionPage(List.of(reference("first", "2026-01-01")), "page-2"));
        collector.pages.add(new CollectorTransactionPage(List.of(reference("second", "2026-01-02")), null));

        assertTrue(collector.collect());
        assertNull(collector.requestedFromDate);
        assertEquals(List.of("signIn", "accounts", "recurringPayments", "page:null", "first", "page:page-2", "second"), collector.steps);
        CollectorProgress progress = collector.savedProgress();
        assertEquals(LocalDate.parse("2026-01-02"), progress.getLastTransactionDate());
        assertEquals(Set.of("second"), progress.getTransactionReferences());
    }

    /**
     * Resume from saved settings in a new collector instance. Older dates and saved references are skipped,
     * but a new reference on the same date must still be collected. Reference order on that date may change.
     */
    @Test
    @DisplayName("Resume without losing new references on the last saved date")
    void resumesFromLastDateAndKeepsReferencesSharingThatDate() throws Exception {
        TestCollector firstRun = new TestCollector();
        firstRun.pages.add(new CollectorTransactionPage(List.of(reference("a", "2026-01-02"), reference("b", "2026-01-02")), null));
        assertTrue(firstRun.collect());

        TestCollector resumed = new TestCollector();
        resumed.settings.putAll(firstRun.getSettings());
        resumed.pages.add(new CollectorTransactionPage(List.of(reference("older", "2026-01-01"), reference("b", "2026-01-02"),
                reference("a", "2026-01-02"), reference("c", "2026-01-02")), null));

        assertTrue(resumed.collect());
        assertEquals(LocalDate.parse("2026-01-02"), resumed.requestedFromDate);
        assertEquals(List.of("c"), resumed.getTransactions().stream().map(TransactionDto::getId).toList());
        assertEquals(Set.of("a", "b", "c"), resumed.savedProgress().getTransactionReferences());
    }

    /**
     * A stop request can arrive while reading transaction details. Keep the completed transaction and its progress,
     * then stop before the next simulated website request.
     */
    @Test
    @DisplayName("Keep completed work when a stop arrives during a transaction")
    void savesCompletedWorkWhenStopArrivesAndDoesNotReadNextPage() throws Exception {
        TestCollector collector = new TestCollector();
        collector.requestStopDuringTransaction = true;
        collector.pages.add(new CollectorTransactionPage(List.of(reference("a", "2026-01-02")), "page-2"));

        assertFalse(collector.collect());
        assertEquals(1, collector.pagesRead);
        assertEquals(1, collector.getTransactions().size());
        assertEquals(Set.of("a"), collector.savedProgress().getTransactionReferences());
    }

    /**
     * A null transaction means that its details are not complete. Return false without saving it,
     * so the next run can try the same reference again.
     */
    @Test
    @DisplayName("Leave progress unchanged when transaction details are incomplete")
    void incompleteTransactionDoesNotAdvanceProgress() throws Exception {
        TestCollector collector = new TestCollector();
        collector.returnIncompleteTransaction = true;
        collector.pages.add(new CollectorTransactionPage(List.of(reference("a", "2026-01-02")), null));

        assertFalse(collector.collect());
        assertTrue(collector.getTransactions().isEmpty());
        assertTrue(collector.getSettings().isEmpty());
    }

    /**
     * Refresh account information before deciding whether to read history.
     * A disabled shopping account must not produce transactions or advance progress.
     */
    @Test
    @DisplayName("Skip history when the shopping account has sync disabled")
    void disabledShoppingAccountSkipsTransactionsButStillSynchronizesAccounts() throws Exception {
        TestCollector collector = new TestCollector();
        collector.purchaseAccount.setSyncEnabled(false);

        assertTrue(collector.collect());
        assertEquals(List.of("signIn", "accounts", "recurringPayments"), collector.steps);
        assertEquals(0, collector.pagesRead);
        assertTrue(collector.getTransactions().isEmpty());
        assertTrue(collector.getSettings().isEmpty());
    }

    /**
     * Dates must remain in ascending order across page boundaries. Otherwise a checkpoint could skip purchases.
     * Reject the older page and keep the progress of transactions already completed.
     */
    @Test
    @DisplayName("Reject history pages that would move progress backwards")
    void rejectsPagesThatGoBackwardsInTime() {
        TestCollector collector = new TestCollector();
        collector.pages.add(new CollectorTransactionPage(List.of(reference("a", "2026-01-02")), "page-2"));
        collector.pages.add(new CollectorTransactionPage(List.of(reference("b", "2026-01-01")), null));

        assertThrows(CollectError.class, collector::collect);
        assertEquals(1, collector.getTransactions().size());
        assertEquals(LocalDate.parse("2026-01-02"), collector.savedProgress().getLastTransactionDate());
    }

    /**
     * Check the service registration used to find this collector.
     * Update the expected class when renaming or replacing the collector.
     */
    @Test
    @DisplayName("Discover the collector through the Java service registration")
    void pluginIsDiscoverable() {
        assertTrue(ServiceLoader.load(CollectorPlugin.class).stream().anyMatch(provider -> provider.type() == StarterCollector.class));
    }

    private static CollectorPendingTransaction reference(String id, String date) {
        return new CollectorPendingTransaction(id, LocalDate.parse(date));
    }

    /**
     * Replace only website operations with predictable test data. This is a test of the shopping history flow,
     * so one shopping account is enough and transaction details contain only the fields needed by these tests.
     * <p>
     * The stop check in {@link #readTransactionPage(AccountDto, LocalDate, String)} represents the check normally made before a network request.
     * Tests using it verify the caller's response to that stop, not the network provider itself.
     */
    private static class TestCollector extends StarterCollector {
        final List<String> steps = new ArrayList<>();
        final List<CollectorTransactionPage> pages = new ArrayList<>();
        final AccountDto purchaseAccount = new AccountDto();
        LocalDate requestedFromDate;
        int pagesRead;
        boolean requestStopDuringTransaction;
        boolean returnIncompleteTransaction;

        TestCollector() {
            purchaseAccount.setId("purchases");
            purchaseAccount.setUuid("purchase-uuid");
            purchaseAccount.setType(AccountDto.AccountDtoType.SHOPPING);
            purchaseAccount.setSyncEnabled(true);
            accounts.put(purchaseAccount.getId(), purchaseAccount);
        }

        CollectorProgress savedProgress() {
            return loadProgress();
        }

        @Override
        protected void signIn() {
            steps.add("signIn");
        }

        @Override
        protected void synchronizeAccounts() {
            steps.add("accounts");
        }

        @Override
        protected void synchronizeRecurringPayments() {
            steps.add("recurringPayments");
        }

        @Override
        protected CollectorTransactionPage readTransactionPage(AccountDto account, LocalDate fromDate, String nextPage) {
            // Simulate the stop check made before a website request.
            if (mustStop()) {
                throw new MustStopException();
            }
            assertSame(purchaseAccount, account);
            String expectedPageToken = pagesRead == 0 ? null : pages.get(pagesRead - 1).nextPage;
            assertEquals(expectedPageToken, nextPage, "Follow the token returned by the previous page.");
            assertTrue(pagesRead < pages.size(), "The test must provide every requested history page.");
            requestedFromDate = fromDate;
            steps.add("page:" + nextPage);
            return pages.get(pagesRead++);
        }

        @Override
        protected TransactionDto collectTransaction(AccountDto account, CollectorPendingTransaction pendingTransaction) {
            steps.add(pendingTransaction.id);
            if (returnIncompleteTransaction) {
                return null;
            }
            TransactionDto transaction = new TransactionDto();
            transaction.setId(pendingTransaction.id);
            transaction.setAccountUuid(account.getUuid());
            if (requestStopDuringTransaction) {
                stop();
            }
            return transaction;
        }
    }
}
