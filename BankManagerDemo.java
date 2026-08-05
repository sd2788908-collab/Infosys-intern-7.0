import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

enum AccountType {
    SAVINGS(10_000),
    CURRENT(20_000);

    private final long openingBalance;

    AccountType(long openingBalance) {
        this.openingBalance = openingBalance;
    }

    public long getOpeningBalance() {
        return openingBalance;
    }

    public static AccountType fromInput(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Account type cannot be null.");
        }

        String normalized = input.trim().toUpperCase();
        switch (normalized) {
            case "S":
            case "SAVINGS":
                return SAVINGS;
            case "C":
            case "CURRENT":
                return CURRENT;
            default:
                throw new IllegalArgumentException("Invalid account type. Use SAVINGS or CURRENT.");
        }
    }
}

class BankAccount {
    private static final AtomicLong SEQUENCE = new AtomicLong(1000000000L);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String accountNumber;
    private final String customerName;
    private final AccountType accountType;
    private long balance;
    private final List<String> transactionHistory = new ArrayList<>();

    public BankAccount(String customerName, AccountType accountType) {
        if (customerName == null || customerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name cannot be empty.");
        }
        if (accountType == null) {
            throw new IllegalArgumentException("Account type cannot be null.");
        }

        this.accountNumber = generateAccountNumber();
        this.customerName = customerName.trim();
        this.accountType = accountType;
        this.balance = accountType.getOpeningBalance();

        recordTransaction("ACCOUNT OPENED", balance, "Opening balance credited");
    }

    private static String generateAccountNumber() {
        return "HDFC" + SEQUENCE.getAndIncrement();
    }

    private void recordTransaction(String action, long amount, String note) {
        String entry = String.format(
                "%s | %-15s | Amount: %d | Balance: %d | %s",
                LocalDateTime.now().format(FORMATTER),
                action,
                amount,
                balance,
                note
        );
        transactionHistory.add(entry);
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public long getBalance() {
        return balance;
    }

    public List<String> getTransactionHistory() {
        return Collections.unmodifiableList(transactionHistory);
    }

    public void deposit(long amount) {
        validateAmount(amount);
        balance += amount;
        recordTransaction("DEPOSIT", amount, "Cash deposited");
    }

    public void withdraw(long amount) {
        validateAmount(amount);

        if (amount > balance) {
            throw new IllegalStateException(
                    "Insufficient balance. Available: " + balance + ", Requested: " + amount);
        }

        balance -= amount;
        recordTransaction("WITHDRAW", amount, "Cash withdrawn");
    }

    public void transferOut(long amount, String toAccountNumber) {
        validateAmount(amount);

        if (amount > balance) {
            throw new IllegalStateException(
                    "Insufficient balance for transfer. Available: " + balance + ", Requested: " + amount);
        }

        balance -= amount;
        recordTransaction("TRANSFER OUT", amount, "To " + toAccountNumber);
    }

    public void transferIn(long amount, String fromAccountNumber) {
        validateAmount(amount);
        balance += amount;
        recordTransaction("TRANSFER IN", amount, "From " + fromAccountNumber);
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
    }

    public String summary() {
        return String.format(
                "%-14s | %-20s | %-8s | Balance: %,d",
                accountNumber,
                customerName,
                accountType,
                balance
        );
    }

    public void printDetails() {
        System.out.println("Account Number : " + accountNumber);
        System.out.println("Customer Name  : " + customerName);
        System.out.println("Account Type   : " + accountType);
        System.out.printf("Balance        : %,d%n", balance);
    }

    public void printStatement() {
        System.out.println("\n--- Statement for " + accountNumber + " ---");
        if (transactionHistory.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        transactionHistory.forEach(System.out::println);
    }

    @Override
    public String toString() {
        return summary();
    }
}

class BankManagerSystem {
    private final Map<String, BankAccount> accountsByNumber = new HashMap<>();
    private final List<BankAccount> accounts = new ArrayList<>();

    public BankAccount openAccount(String customerName, AccountType accountType) {
        BankAccount account = new BankAccount(customerName, accountType);
        accounts.add(account);
        accountsByNumber.put(account.getAccountNumber(), account);
        return account;
    }

    public Optional<BankAccount> findAccount(String accountNumber) {
        if (accountNumber == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsByNumber.get(normalizeAccountNumber(accountNumber)));
    }

    public void deposit(String accountNumber, long amount) {
        requireAccount(accountNumber).deposit(amount);
    }

    public void withdraw(String accountNumber, long amount) {
        requireAccount(accountNumber).withdraw(amount);
    }

    public void transfer(String fromAccountNumber, String toAccountNumber, long amount) {
        String from = normalizeAccountNumber(fromAccountNumber);
        String to = normalizeAccountNumber(toAccountNumber);

        if (from.equalsIgnoreCase(to)) {
            throw new IllegalArgumentException("Source and destination account numbers cannot be the same.");
        }

        BankAccount sender = requireAccount(from);
        BankAccount receiver = requireAccount(to);

        sender.transferOut(amount, to);
        receiver.transferIn(amount, from);
    }

    public void printAccountDetails(String accountNumber) {
        requireAccount(accountNumber).printDetails();
    }

    public void printAllAccounts() {
        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }

        System.out.println("\n--- All Accounts (" + accounts.size() + ") ---");
        accounts.stream()
                .sorted(Comparator.comparing(BankAccount::getCustomerName, String.CASE_INSENSITIVE_ORDER))
                .forEach(System.out::println);
    }

    public void printAccountsByType(AccountType type) {
        List<BankAccount> matches = accounts.stream()
                .filter(a -> a.getAccountType() == type)
                .sorted(Comparator.comparingLong(BankAccount::getBalance).reversed())
                .collect(Collectors.toList());

        System.out.println("\n--- Accounts of type " + type + " (" + matches.size() + ") ---");
        if (matches.isEmpty()) {
            System.out.println("No accounts of this type found.");
            return;
        }
        matches.forEach(System.out::println);
    }

    public void printHighBalanceAccounts(long minimumBalance) {
        List<String> matches = accounts.stream()
                .filter(a -> a.getBalance() >= minimumBalance)
                .sorted(Comparator.comparingLong(BankAccount::getBalance).reversed())
                .map(BankAccount::summary)
                .collect(Collectors.toList());

        System.out.println("\n--- Accounts with balance >= " + minimumBalance + " (" + matches.size() + ") ---");
        if (matches.isEmpty()) {
            System.out.println("No matching accounts found.");
            return;
        }
        matches.forEach(System.out::println);
    }

    public void printTransactionHistory(String accountNumber) {
        requireAccount(accountNumber).printStatement();
    }

    public void printStatistics() {
        System.out.println("\n--- Bank Statistics ---");
        System.out.println("Total accounts: " + accounts.size());

        if (accounts.isEmpty()) {
            return;
        }

        Map<AccountType, Long> countsByType = accounts.stream()
                .collect(Collectors.groupingBy(BankAccount::getAccountType, LinkedHashMap::new, Collectors.counting()));
        countsByType.forEach((type, count) -> System.out.println(type + " accounts: " + count));

        long totalBalance = accounts.stream().mapToLong(BankAccount::getBalance).sum();
        double averageBalance = totalBalance / (double) accounts.size();
        System.out.printf("Total balance held: %,d%n", totalBalance);
        System.out.printf("Average balance   : %,.2f%n", averageBalance);
    }

    private BankAccount requireAccount(String accountNumber) {
        return findAccount(accountNumber)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + accountNumber));
    }

    private String normalizeAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            throw new IllegalArgumentException("Account number cannot be null.");
        }
        return accountNumber.trim().toUpperCase();
    }
}

public class BankManagerDemo {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final BankManagerSystem BANK = new BankManagerSystem();

    public static void main(String[] args) {
        System.out.println("===================================");
        System.out.println("      BANK MANAGEMENT SYSTEM       ");
        System.out.println("===================================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ");

            try {
                switch (choice) {
                    case 1:
                        openAccount();
                        break;
                    case 2:
                        enquiryAccount();
                        break;
                    case 3:
                        depositMoney();
                        break;
                    case 4:
                        withdrawMoney();
                        break;
                    case 5:
                        transferMoney();
                        break;
                    case 6:
                        BANK.printAllAccounts();
                        break;
                    case 7:
                        showAccountsByType();
                        break;
                    case 8:
                        showHighBalanceAccounts();
                        break;
                    case 9:
                        showTransactionHistory();
                        break;
                    case 10:
                        BANK.printStatistics();
                        break;
                    case 11:
                        System.out.println("Exiting... Thank you!");
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid choice. Please enter a number between 1 and 11.");
                }
            } catch (RuntimeException ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }

        SCANNER.close();
    }

    private static void printMenu() {
        System.out.println("\n------------- MENU -------------");
        System.out.println("1.  Open Account");
        System.out.println("2.  Account Enquiry");
        System.out.println("3.  Deposit Money");
        System.out.println("4.  Withdraw Money");
        System.out.println("5.  Transfer Money");
        System.out.println("6.  Show All Accounts");
        System.out.println("7.  Show Accounts By Type");
        System.out.println("8.  Show Accounts With High Balance");
        System.out.println("9.  Show Transaction History");
        System.out.println("10. Show Statistics");
        System.out.println("11. Exit");
        System.out.println("---------------------------------");
    }

    private static void openAccount() {
        String name = readNonEmptyString("Enter customer name: ");
        AccountType type = readAccountType("Enter account type (Savings/Current): ");

        BankAccount account = BANK.openAccount(name, type);
        System.out.println("Account created successfully.");
        System.out.println("Account Number : " + account.getAccountNumber());
        System.out.printf("Opening Balance: %,d%n", account.getBalance());
    }

    private static void enquiryAccount() {
        String accountNumber = readNonEmptyString("Enter account number: ");
        BANK.printAccountDetails(accountNumber);
    }

    private static void depositMoney() {
        String accountNumber = readNonEmptyString("Enter account number: ");
        long amount = readPositiveLong("Enter deposit amount: ");
        BANK.deposit(accountNumber, amount);
        System.out.println("Amount deposited successfully.");
    }

    private static void withdrawMoney() {
        String accountNumber = readNonEmptyString("Enter account number: ");
        long amount = readPositiveLong("Enter withdraw amount: ");
        BANK.withdraw(accountNumber, amount);
        System.out.println("Amount withdrawn successfully.");
    }

    private static void transferMoney() {
        String fromAccount = readNonEmptyString("Enter sender account number: ");
        String toAccount = readNonEmptyString("Enter receiver account number: ");
        long amount = readPositiveLong("Enter transfer amount: ");
        BANK.transfer(fromAccount, toAccount, amount);
        System.out.println("Transfer completed successfully.");
    }

    private static void showAccountsByType() {
        AccountType type = readAccountType("Enter account type (Savings/Current): ");
        BANK.printAccountsByType(type);
    }

    private static void showHighBalanceAccounts() {
        long minimumBalance = readPositiveLong("Enter minimum balance: ");
        BANK.printHighBalanceAccounts(minimumBalance);
    }

    private static void showTransactionHistory() {
        String accountNumber = readNonEmptyString("Enter account number: ");
        BANK.printTransactionHistory(accountNumber);
    }

    // ---------- Input helpers ----------

    private static String readNonEmptyString(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = SCANNER.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("Input cannot be empty. Try again.");
        }
    }

    private static AccountType readAccountType(String prompt) {
        while (true) {
            String input = readNonEmptyString(prompt);
            try {
                return AccountType.fromInput(input);
            } catch (IllegalArgumentException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String raw = SCANNER.nextLine().trim();
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private static long readPositiveLong(String prompt) {
        while (true) {
            System.out.print(prompt);
            String raw = SCANNER.nextLine().trim();
            try {
                long value = Long.parseLong(raw);
                if (value <= 0) {
                    System.out.println("Please enter a number greater than zero.");
                    continue;
                }
                return value;
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}
