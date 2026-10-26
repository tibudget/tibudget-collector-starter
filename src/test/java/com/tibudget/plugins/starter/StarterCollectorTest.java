package com.tibudget.plugins.starter;

import com.google.gson.JsonObject;
import com.tibudget.api.CollectorPlugin;
import com.tibudget.api.exceptions.CollectError;
import com.tibudget.dto.AccountDto;
import com.tibudget.dto.TransactionDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

class StarterCollectorTest {

    @Test
    void stopDoesNotPreventSignInOrAccountSynchronization() throws Exception {
        TestCollector collector = new TestCollector();
        collector.stop();

        assertFalse(collector.collect());
        assertEquals(List.of("signIn", "accounts"), collector.steps);
        assertEquals(0, collector.pagesRead);
    }

    @Test
    void collectsEachPageBeforeRequestingTheNextOne() throws Exception {
        TestCollector collector = new TestCollector();
        collector.pages.add(new StarterCollector.TransactionPage(List.of(reference("first", "2026-01-01")), "page-2"));
        collector.pages.add(new StarterCollector.TransactionPage(List.of(reference("second", "2026-01-02")), null));

        assertTrue(collector.collect());
        assertNull(collector.requestedFromDate);
        assertEquals(List.of("signIn", "accounts", "page:null", "first", "page:page-2", "second"), collector.steps);
        JsonObject progress = progress(collector);
        assertEquals("2026-01-02", progress.get("lastTransactionDate").getAsString());
        assertEquals("[\"second\"]", progress.get("transactionReferences").toString());
    }

    @Test
    void resumesFromPreviousDayAndKeepsReferencesSharingTheLastDate() throws Exception {
        TestCollector firstRun = new TestCollector();
        firstRun.pages.add(new StarterCollector.TransactionPage(List.of(reference("a", "2026-01-02"), reference("b", "2026-01-02")), null));
        assertTrue(firstRun.collect());

        TestCollector resumed = new TestCollector();
        resumed.settings.putAll(firstRun.settings);
        resumed.pages.add(new StarterCollector.TransactionPage(List.of(reference("older", "2026-01-01"), reference("b", "2026-01-02"),
                reference("a", "2026-01-02"), reference("c", "2026-01-02")), null));

        assertTrue(resumed.collect());
        assertEquals(LocalDate.parse("2026-01-01"), resumed.requestedFromDate);
        assertEquals(List.of("c"), resumed.getTransactions().stream().map(TransactionDto::getId).toList());
        assertEquals("[\"a\",\"b\",\"c\"]", progress(resumed).get("transactionReferences").toString());
    }

    @Test
    void savesCompletedWorkWhenStopArrivesAndDoesNotReadNextPage() throws Exception {
        TestCollector collector = new TestCollector();
        collector.stopAfterTransaction = true;
        collector.pages.add(new StarterCollector.TransactionPage(List.of(reference("a", "2026-01-02")), "page-2"));

        assertFalse(collector.collect());
        assertEquals(1, collector.pagesRead);
        assertEquals(1, collector.getTransactions().size());
        assertEquals("[\"a\"]", progress(collector).get("transactionReferences").toString());
    }

    @Test
    void incompleteTransactionDoesNotAdvanceProgress() throws Exception {
        TestCollector collector = new TestCollector();
        collector.incomplete = true;
        collector.pages.add(new StarterCollector.TransactionPage(List.of(reference("a", "2026-01-02")), null));

        assertFalse(collector.collect());
        assertTrue(collector.transactions.isEmpty());
        assertTrue(collector.settings.isEmpty());
    }

    @Test
    void disabledShoppingAccountSkipsTransactionsButStillSynchronizesAccounts() throws Exception {
        TestCollector collector = new TestCollector();
        collector.purchaseAccount.setSyncEnabled(false);

        assertTrue(collector.collect());
        assertEquals(List.of("signIn", "accounts"), collector.steps);
        assertEquals(0, collector.pagesRead);
    }

    @Test
    void rejectsPagesThatGoBackwardsInTime() {
        TestCollector collector = new TestCollector();
        collector.pages.add(new StarterCollector.TransactionPage(List.of(reference("a", "2026-01-02")), "page-2"));
        collector.pages.add(new StarterCollector.TransactionPage(List.of(reference("b", "2026-01-01")), null));

        assertThrows(CollectError.class, collector::collect);
        assertEquals(1, collector.transactions.size());
        assertEquals("2026-01-02", progress(collector).get("lastTransactionDate").getAsString());
    }

    @Test
    void pluginIsDiscoverable() {
        assertTrue(ServiceLoader.load(CollectorPlugin.class).stream().anyMatch(provider -> provider.type() == StarterCollector.class));
    }

    private static StarterCollector.PendingTransaction reference(String id, String date) {
        return new StarterCollector.PendingTransaction(id, LocalDate.parse(date));
    }

    private static JsonObject progress(TestCollector collector) {
        return collector.gson.fromJson(collector.settings.get("starter.progress"), JsonObject.class);
    }

    private static class TestCollector extends StarterCollector {
        final List<String> steps = new ArrayList<>();
        final List<TransactionPage> pages = new ArrayList<>();
        final AccountDto purchaseAccount = new AccountDto();
        LocalDate requestedFromDate;
        int pagesRead;
        boolean stopAfterTransaction;
        boolean incomplete;

        TestCollector() {
            purchaseAccount.setId("purchases");
            purchaseAccount.setUuid("purchase-uuid");
            purchaseAccount.setType(AccountDto.AccountDtoType.SHOPPING);
            purchaseAccount.setSyncEnabled(true);
            accounts.put(purchaseAccount.getId(), purchaseAccount);
            AccountDto loyaltyAccount = new AccountDto();
            loyaltyAccount.setId("loyalty");
            loyaltyAccount.setType(AccountDto.AccountDtoType.LOYALTY_CARD);
            loyaltyAccount.setSyncEnabled(true);
            accounts.put(loyaltyAccount.getId(), loyaltyAccount);
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
        protected TransactionPage readTransactionPage(AccountDto account, LocalDate fromDate, String nextPage) {
            assertSame(purchaseAccount, account);
            requestedFromDate = fromDate;
            steps.add("page:" + nextPage);
            return pages.get(pagesRead++);
        }

        @Override
        protected TransactionDto collectTransaction(AccountDto account, PendingTransaction pendingTransaction) {
            steps.add(pendingTransaction.id);
            if (incomplete) {
                return null;
            }
            TransactionDto transaction = new TransactionDto();
            transaction.setId(pendingTransaction.id);
            transaction.setAccountUuid(account.getUuid());
            if (stopAfterTransaction) {
                stop();
            }
            return transaction;
        }
    }
}
