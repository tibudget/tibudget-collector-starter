package com.tibudget.plugins.starter;

import com.tibudget.api.Input;
import com.tibudget.api.OTPProvider;
import com.tibudget.api.exceptions.*;
import com.tibudget.dto.*;
import com.tibudget.utils.*;
import com.tibudget.utils.exception.MustStopException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Collector for the Tibu demo website. Adapt the website methods for your own collector.
 * Use {@link #internetProvider} for requests and downloads.
 * See {@link com.tibudget.api.CollectorPlugin} for the API contract.
 */
public class StarterCollector extends AbstractCollectorPlugin {

    // Time zone is usually fixed for a national shop but make sure to affect the appropriate time zone if it is variable
    private static final ZoneId ACCOUNT_TIME_ZONE = ZoneId.of("Europe/Paris");

    // To find the UUID of the counterparty associated to your website, then create an account on tibudget.com/pro
    // You will find a search field to search for it.
    private static final String DEMO_COUNTERPARTY_UUID = UUID.nameUUIDFromBytes("tibu:maison-demo".getBytes(StandardCharsets.UTF_8)).toString();

    private static final String SINGLE_SUBSCRIPTION_ID = "maison-plus";
    private Document accountPage;
    private final List<CollectorPendingTransaction> subscriptionInvoices = new ArrayList<>();
    private LocalDate previousPageEndDate;

    @Input(order = 1)
    public String login;

    @Input(order = 2, password = true)
    public String password;

    @Input(order = 3, required = false)
    public boolean withOtp;

    /**
     * If your collector contains configuration fields, then implements validate() to check values
     * @return List of messages or an empty list if every field are correct
     */
    @Override
    public List<MessageDto> validate() {
        List<MessageDto> messages = new ArrayList<>();
        if (login == null || login.isBlank()) {
            messages.add(new MessageDto(MessageDto.MessageType.ERROR, "login", "form.error.login.required"));
        }
        if (password == null || password.isBlank()) {
            messages.add(new MessageDto(MessageDto.MessageType.ERROR, "password", "form.error.password.required"));
        }
        return messages;
    }

    @Override
    public String getDomain() {
        // Must be the root of the domain
        return "http://localhost:18743/";
    }

    /**
     * Connect to the website, update accounts and recurring payments, then collect purchases.
     *
     * @return true when finished, or false when more work remains
     */
    @Override
    public boolean collect() throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
        try {
            setMinIntervalBetweenRequestInMs(200);
            signIn();
            synchronizeAccounts();
            synchronizeRecurringPayments();
            return synchronizeTransactions();
        }
        catch (MustStopException e) {
            // We stops before the end so return false
            return false;
        }
    }

    /**
     * Reuse a valid session or sign in using the website's login flow.
     * Be aware that cookies from last collect are restored and so will not need to log in everytime.
     * Use {@link #otpProvider} if a code is needed.
     * For OpenID, see {@link #getOpenIdJSONConfiguration()} and {@link #initConnection(java.net.URI)}.
     */
    protected void signIn() throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
        accountPage = get("/fr/compte");
        if (accountPage.selectFirst(".account-heading") == null) {

            // We are not connected, make sure the login form is present
            required(accountPage, "form:has(input[name=login])");

            // Fill in login form fields
            Map<String, String> fields = new HashMap<>();
            fields.put("login", login);
            fields.put("password", password);
            if (withOtp) {
                fields.put("with_otp", "1");
            }

            // Submit the login form
            accountPage = submitForm(accountPage, "form:has(input[name=login])", fields);
            if (accountPage.selectFirst("input[name=otp]") != null) {

                // The website requires an OTP code
                if (otpProvider == null) {
                    // We have no way to get it so we throw an AccessDeny exception with a clear message that explain
                    // to the end user why the collector cannot log in
                    throw new AccessDeny("error.otp.no_provider");
                }

                // Request a code to the OTP provider
                String code = otpProvider.getCode(OTPProvider.Channel.SMS, "Maison démo", OTPProvider.PATTERN_4_DIGIT, "starter.otp.prompt");
                if (code == null) {
                    throw new AccessDeny("error.otp.no_code");
                }

                // Submit the OTP code
                accountPage = submitForm(accountPage, "form:has(input[name=otp])", Map.of("otp", code));

                if (accountPage.selectFirst(".account-heading") == null) {
                    // OTP code has been refused
                    throw new AccessDeny("error.otp.code_refused");
                }
            }
            else if (accountPage.selectFirst(".account-heading") == null) {
                // Login/password were wrong
                throw new AccessDeny("error.login.fail");
            }
        }

        // Define a configuration name so the user know exactly what it is about
        setConfigurationName("Maison démo (" + CollectorUtils.maskEmail(login) + ")");

        // ID hash must identify the configuration so here we use the login, that's what should be
        // used in most case. This is to avoid duplicates in Tibu app.
        setConfigurationIdHash(CollectorUtils.hash(login));
    }

    /**
     * Update existing accounts in {@link #accounts}, or create them if they do not exist.
     * Use a stable website identifier for {@link AccountDto#getId()}: for example a customer ID for purchases
     * or a card number for a loyalty card. It must identify the same account across runs and collectors,
     * not a page position or a new random value. Use this ID as the map key.
     * Preserve existing UUIDs and synchronization choices. Generate a UUID only for a new account.
     * See {@link AccountDto} for the fields to fill in.
     */
    protected void synchronizeAccounts() throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
        // accounts is a map in which keys are ID of account
        AccountDto shoppingAccount = accounts.get(login);
        if (shoppingAccount == null) {
            shoppingAccount = new AccountDto(AccountDto.AccountDtoType.SHOPPING, "Maison démo", DEMO_COUNTERPARTY_UUID, "EUR", ACCOUNT_TIME_ZONE.getId(), 0);
            // The ID of the account must be stable and identify the same account across runs and collectors. Yes, across collectors because
            // different collectors may be used to collect same account from the same counterparty (not concurrently but a user may switch to another collector).
            shoppingAccount.setId(login);
            // On creation sync is enabled by default
            shoppingAccount.setSyncEnabled(true);
            accounts.put(login, shoppingAccount);
        }
        if (shoppingAccount.isSyncEnabled()) {
            // Shopping account have normally a balance of 0
            shoppingAccount.setCurrentBalance(0);
            // Set these values again in case the previous collector (if any) set it wrong
            shoppingAccount.setLabel("Maison démo");
            shoppingAccount.setCurrencyCode("EUR");
            shoppingAccount.setTimeZoneId(ACCOUNT_TIME_ZONE.getId());
            shoppingAccount.setType(AccountDto.AccountDtoType.SHOPPING);
        }

        Element loyalty = required(accountPage, ".loyalty");
        Element progress = required(loyalty, "progress");
        int pointsStr = Integer.parseInt(progress.attr("value"));
        String cardNumber = required(loyalty, "figcaption").text();
        AccountDto loyaltyAccount = accounts.get(cardNumber);
        if (loyaltyAccount == null) {
            loyaltyAccount = new AccountDto(AccountDto.AccountDtoType.LOYALTY_CARD, "Maison démo", DEMO_COUNTERPARTY_UUID, "POINTS", ACCOUNT_TIME_ZONE.getId(), pointsStr);
            // The card number is a good stable ID for this account
            loyaltyAccount.setId(cardNumber);
            // On creation sync is enabled by default
            loyaltyAccount.setSyncEnabled(true);
            accounts.put(cardNumber, loyaltyAccount);
        }
        if (loyaltyAccount.isSyncEnabled()) {
            loyaltyAccount.setCurrentBalance(pointsStr);
            // Set these values again in case the previous collector (if any) set it wrong
            loyaltyAccount.setLabel("Maison démo");
            loyaltyAccount.setCurrencyCode("POINTS");
            loyaltyAccount.setTimeZoneId(ACCOUNT_TIME_ZONE.getId());
            loyaltyAccount.setType(AccountDto.AccountDtoType.LOYALTY_CARD);
            loyaltyAccount.setMetadata(AccountDto.METADATA_LOYALTY_CARD_REFERENCE, cardNumber);
            loyaltyAccount.setMetadata(AccountDto.METADATA_LOYALTY_CARD_BAR_CODE_TYPE, "CODE_128");
            loyaltyAccount.setMetadata(AccountDto.METADATA_LOYALTY_CARD_BG_COLOR, "#FFFFFF");
        }
    }

    /**
     * Update existing subscriptions or installment plans in {@link #recurringPayments}, or create new ones.
     * Use a stable website reference as the ID. Preserve existing UUIDs.
     * Link each plan to its account. Leave this step empty if the website has no recurring payments.
     * See {@link com.tibudget.dto.RecurringPaymentDto} for the fields to fill in.
     */
    protected void synchronizeRecurringPayments() throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
        subscriptionInvoices.clear();
        AccountDto shoppingAccount = accounts.get(login);
        if (!shoppingAccount.isSyncEnabled()) {
            return;
        }
        // Here the shop has only one subscription plan and because we don't want to use a specific ID we get the first and only one subscription
        RecurringPaymentDto plan = recurringPayments.values().stream().findFirst().orElse(null);
        if (plan == null) {
            plan = new RecurringPaymentDto(SINGLE_SUBSCRIPTION_ID, UUID.randomUUID().toString(), shoppingAccount.getUuid());
            recurringPayments.put(SINGLE_SUBSCRIPTION_ID, plan);
        }
        else if (!plan.getAccountUuid().equals(shoppingAccount.getUuid())){
            throw new CollectError("error.recurring_payments.wrong_account");
        }
        Element subscription = required(accountPage, ".subscription");
        // Set these values again in case the previous collector (if any) set it wrong
        plan.setLabel("Maison Plus");
        plan.setExpectedAmount(-CollectorUtils.elementSelectionToAmount(subscription, "h2 small", Locale.FRANCE).getAmount());
        plan.setAcceptableAmountVariationRatio(0.0);
        plan.setRecurrenceInterval(1);
        plan.setRecurrenceUnit(RecurringPaymentDto.RecurrenceUnit.MONTH);
        plan.setState(RecurringPaymentDto.State.ACTIVE);
    }

    /**
     * Collect purchases from the {@link AccountDto.AccountDtoType#SHOPPING} account, oldest first.
     * Process one history page at a time and save progress after each complete transaction.
     */
    private boolean synchronizeTransactions() throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
        AccountDto shoppingAccount = accounts.values().stream().filter(account -> account.getType() == AccountDto.AccountDtoType.SHOPPING).findFirst().orElse(null);
        if (shoppingAccount == null) {
            throw new CollectError("No account to synchronize?");
        }
        if (!shoppingAccount.isSyncEnabled()) {
            // Nothing to synchronize here, finished.
            return true;
        }

        // Load the last progress state to start from the last time
        CollectorProgress progress = loadProgress();
        LocalDate fromDate = progress.getLastTransactionDate();
        LocalDate previousDate = null;
        String nextPage = null;
        do {
            CollectorTransactionPage page = readTransactionPage(shoppingAccount, fromDate, nextPage);
            for (CollectorPendingTransaction pendingTransaction : page.transactions) {

                // Reject history that would advance progress past older, uncollected transactions.
                if (previousDate != null && pendingTransaction.date.isBefore(previousDate)) {
                    throw new CollectError("Transaction pages must be ordered from oldest to newest.");
                }
                previousDate = pendingTransaction.date;

                // Dates before the checkpoint are complete; on that date, skip only saved references.
                if (progress.getLastTransactionDate() != null && (pendingTransaction.date.isBefore(progress.getLastTransactionDate())
                        || (pendingTransaction.date.equals(progress.getLastTransactionDate()) && progress.getTransactionReferences().contains(pendingTransaction.id)))) {
                    continue;
                }

                // Fully collect the transaction
                TransactionDto transaction = collectTransaction(shoppingAccount, pendingTransaction);
                if (transaction == null) {
                    return false;
                }

                // Keep completed work even if a stop arrived during the final request.
                transactions.add(transaction);
                saveProgress(progress, pendingTransaction);
            }
            nextPage = page.nextPage;
        } while (nextPage != null);
        return !mustStop();
    }

    /**
     * Read one page of history, oldest first, without loading the full history.
     * Start the website search the day before fromDate, or at the oldest purchase if fromDate is null.
     * Keep the same search range while following the website's pages.
     *
     * @param account the shopping account
     * @param fromDate the last collected date, in the account's time zone, or null on the first run
     * @param nextPage the website's page token, or null for the first page
     * @return dated references in ascending order, with the next page token or null when finished
     */
    protected CollectorTransactionPage readTransactionPage(AccountDto account, LocalDate fromDate, String nextPage) throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
        int pageNumber;
        if (nextPage == null) {
            // First page
            previousPageEndDate = null;

            // Get the last page number for orders to get the oldest ones first
            pageNumber = accountPage.select(".pagination a").stream().mapToInt(link -> Integer.parseInt(link.text())).max().orElseThrow();

            // Get the invoice page URL for recurring payments: we need a special treatment for those because they are not in the orders page
            Document invoicesPage = get(required(accountPage, ".subscription a").attr("href"));
            subscriptionInvoices.clear();
            for (Element invoice : invoicesPage.select("article[data-invoice-id]")) {
                subscriptionInvoices.add(createCollectorPendingTransaction(invoice, "data-invoice-id"));
            }
        } else {
            pageNumber = Integer.parseInt(nextPage);
        }

        // Create CollectorPendingTransaction for each order of this page
        Document ordersPage = get("/fr/compte?page=" + pageNumber);
        List<CollectorPendingTransaction> references = new ArrayList<>();
        for (Element order : ordersPage.select("article[data-order-id]")) {
            references.add(createCollectorPendingTransaction(order, "data-order-id"));
        }

        // Merge invoices from recurring payments belonging to this order page's date range.
        LocalDate mostRecentOrderDate = references.stream().map(reference -> reference.date).max(LocalDate::compareTo).orElseThrow();
        for (CollectorPendingTransaction invoice : subscriptionInvoices) {
            if ((previousPageEndDate == null || invoice.date.isAfter(previousPageEndDate)) && (pageNumber == 1 || !invoice.date.isAfter(mostRecentOrderDate))) {
                references.add(invoice);
            }
        }
        previousPageEndDate = mostRecentOrderDate;

        // Remove transaction previous to fromDate because they have been collected in previous collect
        if (fromDate != null) {
            references.removeIf(reference -> reference.date.isBefore(fromDate));
        }

        // Sort transactions in chronological order because we added some from recurring payments
        references.sort(Comparator.comparing((CollectorPendingTransaction reference) -> reference.date).thenComparing(reference -> reference.id));

        return new CollectorTransactionPage(references, pageNumber > 1 ? Integer.toString(pageNumber - 1) : null);
    }

    /**
     * Read the transaction details and available items, payments, and documents.
     * Use the website's stable reference as {@link TransactionDto#getId()} and link the transaction to its account UUID.
     * See {@link TransactionDto}, {@link com.tibudget.dto.ItemDto}, {@link com.tibudget.dto.PaymentDto},
     * and {@link com.tibudget.dto.FileDto} for the fields to fill in.
     * Do not add the result to {@link #transactions} or save progress here; the caller does that.
     *
     * @param account the shopping account
     * @param pendingTransaction the reference and date from the history
     * @return the complete transaction, or null if collection stopped before it was complete
     */
    protected TransactionDto collectTransaction(AccountDto account, CollectorPendingTransaction pendingTransaction) throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
        Element details = pendingTransaction.details;

        // Is a recurring transaction?
        boolean subscription = details.hasAttr("data-invoice-id");

        // Date
        Date date = Date.from(pendingTransaction.date.atStartOfDay(ACCOUNT_TIME_ZONE).toInstant());

        // Label
        String label = CollectorUtils.elementSelectionToText(details, "h2,h3");

        // Amount
        AmountDto amountDto = AmountParser.parse(required(details, ".order-footer > strong").text(), Locale.FRANCE);

        // The amount must be seen from the user's point of view, since it is a purchase the amount must be negative
        TransactionDto transaction = new TransactionDto(pendingTransaction.id, account.getUuid(), TransactionDto.TransactionDtoType.PURCHASE, date, date, label, label, -amountDto.getAmount(), amountDto.getCurrencyIsoCode());
        transaction.setCounterpartyUuid(DEMO_COUNTERPARTY_UUID);

        if (subscription) {
            transaction.setRecurrentPaymentUuid(recurringPayments.get(SINGLE_SUBSCRIPTION_ID).getUuid());
            transaction.addItem(new ItemDto("Maison Plus", -transaction.getAmount(), 1, ItemDto.QuantityUnit.MONTH));
        } else {
            switch (details.attr("data-status")) {
                case "paid" -> transaction.setState(TransactionDto.TransactionDtoState.COMPLETED);
                case "pending" -> transaction.setState(TransactionDto.TransactionDtoState.PENDING);
                case "cancelled" -> transaction.setState(TransactionDto.TransactionDtoState.DELETED);
                default -> throw new CollectError("starter.history.invalid");
            }
            for (Element itemElement : details.select(".items > li")) {
                Integer itemQuantity = CollectorUtils.elementSelectionToInteger(itemElement, ".item-name .muted");
                String itemLabel = CollectorUtils.elementSelectionToText(itemElement, ".item-name strong");
                AmountDto itemAmountDto = CollectorUtils.elementSelectionToAmount(itemElement, "li > strong", Locale.FRANCE);
                ItemDto item = new ItemDto(itemLabel, itemAmountDto.getAmount(), itemQuantity, ItemDto.QuantityUnit.UNIT);
                Element image = itemElement.selectFirst("img");
                if (image != null) {
                    FileDto photo = downloadAttachment(image.attr("src"), FileDto.FileDtoType.IMAGE, item.getLabel(), "image/jpeg");
                    if (photo != null) {
                        item.addFile(photo);
                    }
                }
                transaction.addItem(item);
            }
        }
        Element invoice = details.selectFirst("a.invoice");
        if (invoice != null) {
            FileDto file = downloadAttachment(invoice.attr("href"), FileDto.FileDtoType.INVOICE, pendingTransaction.id, "application/pdf");
            if (file != null) {
                transaction.addFile(file);
            }
        }
        return transaction;
    }

    /**
     * Include the CSRF token without sending unchecked checkboxes from the login form.
     */
    private Document submitForm(Document page, String selector, Map<String, String> values) throws CollectError, TemporaryUnavailable {
        Element form = required(page, selector);
        Map<String, String> fields = new HashMap<>(values);
        fields.put("csrf", required(form, "input[name=csrf]").val());
        return postForm(form.attr("action"), fields);
    }

    private CollectorPendingTransaction createCollectorPendingTransaction(Element element, String idAttribute) throws CollectError {
        try {
            return new CollectorPendingTransaction(element.attr(idAttribute), LocalDate.parse(required(element, "time[datetime]").attr("datetime")), element);
        } catch (RuntimeException exception) {
            throw new CollectError("starter.history.invalid", exception);
        }
    }

    /**
     * Used by tests
     */
    public void setLogin(String login) {
        this.login = login;
    }

    /**
     * Used by tests
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Used by tests
     */
    public void setWithOtp(boolean withOtp) {
        this.withOtp = withOtp;
    }
}
