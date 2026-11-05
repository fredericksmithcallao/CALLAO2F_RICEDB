package com.scc.ui;

import com.scc.dao.DatabaseException;
import com.scc.dao.UserDao;
import com.scc.model.AccountStatus;
import com.scc.model.ActivityLog;
import com.scc.model.DeliveryJob;
import com.scc.model.RiceBatch;
import com.scc.model.Role;
import com.scc.model.StockRequest;
import com.scc.model.User;
import com.scc.service.CentralService;

import java.util.ArrayList;
import java.util.List;

public class AdminMenu {

    private final CentralService service;
    private final UserDao userDao;   // shared instance from Main
    private final ConsoleInput in;

    public AdminMenu(CentralService service, UserDao userDao, ConsoleInput in) {
        this.service = service;
        this.userDao = userDao;
        this.in = in;
    }

    public void show(User admin) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("\n--- SYSTEM BOSS/ADMIN MENU (" + admin.fullName() + ") ---");
            System.out.println("1. See Everything (dashboard)");
            System.out.println("2. View All System Users");
            System.out.println("3. View Pending Registrations");
            System.out.println("4. Approve User Account");
            System.out.println("5. Reject Pending Account");
            System.out.println("6. Approve/Reject Stock Transfer Requests");
            System.out.println("7. Manage Accounts (view / block / unblock / delete)");
            System.out.println("8. Update User Role & Status");
            System.out.println("9. View System Audit Logs");
            System.out.println("10. Logout");
            String choice = in.readLine("Choose an option: ");

            try {
                switch (choice) {
                    case "1" -> showDashboard();
                    case "2" -> viewAllUsers();
                    case "3" -> viewPendingRegistrations();
                    case "4" -> approveUserAccount(admin);
                    case "5" -> rejectPendingAccount(admin);
                    case "6" -> handleStockRequests(admin);
                    case "7" -> manageAccounts(admin);
                    case "8" -> updateUserRoleAndStatus(admin);
                    case "9" -> auditLogMenu(admin);
                    case "10" -> {
                        loggedIn = false;
                        System.out.println("Logged out.");
                    }
                    default -> System.out.println("Invalid option.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            } catch (DatabaseException e) {
                System.out.println("Database error: " + e.getMessage());
            }
        }
    }

    private void showDashboard() {
        System.out.println("\n============ CENTRAL DASHBOARD ============");
        System.out.println("Total rice in warehouse stock : " + service.getTotalWarehouseStockKg() + " kg");
        System.out.println("Active deliveries in progress : " + service.countActiveDeliveries());
        System.out.println("Total stock requests logged    : " + service.getAllRequests().size());
        System.out.println("Total delivery jobs logged     : " + service.getAllJobs().size());
        System.out.println("Total registered accounts      : " + userDao.findAll().size());

        System.out.println("\n-- All Rice Batches --");
        for (RiceBatch b : service.getAllBatches()) System.out.println("  " + b);

        System.out.println("\n-- All Stock Requests --");
        for (StockRequest r : service.getAllRequests()) System.out.println("  " + r);

        System.out.println("\n-- All Delivery Jobs --");
        for (DeliveryJob j : service.getAllJobs()) System.out.println("  " + j);
        System.out.println("=============================================");
    }

    /* ---------------------------- USER LISTS & APPROVAL ---------------------------- */

    private void viewAllUsers() {
        List<User> users = userDao.getAllUsers();
        System.out.println("\n-- All System Users --");
        if (users.isEmpty()) {
            System.out.println("No registered accounts.");
            return;
        }
        printUserTable(users);
    }

    private void viewPendingRegistrations() {
        List<User> pending = userDao.getPendingUsers();
        System.out.println("\n-- Pending Registrations --");
        if (pending.isEmpty()) {
            System.out.println("No pending accounts right now.");
            return;
        }
        printUserTable(pending);
    }

    private void approveUserAccount(User admin) {
        List<User> pending = userDao.getPendingUsers();
        if (pending.isEmpty()) {
            System.out.println("No pending accounts right now.");
            return;
        }
        System.out.println("\n-- Pending Registrations --");
        printUserTable(pending);

        User target = askPendingUser(pending, "Enter the User ID to approve (0 to cancel): ");
        if (target == null) return;

        if (service.approveUser(admin.username(), target.id())) {
            System.out.println("Success! Account approved: " + target.username() + " (ID " + target.id() + ")");
        } else {
            System.out.println("Could not approve that account.");
        }
    }

    private void rejectPendingAccount(User admin) {
        List<User> pending = userDao.getPendingUsers();
        if (pending.isEmpty()) {
            System.out.println("No pending accounts right now.");
            return;
        }
        System.out.println("\n-- Pending Registrations --");
        printUserTable(pending);

        User target = askPendingUser(pending, "Enter the User ID to reject (0 to cancel): ");
        if (target == null) return;

        service.setAccountStatus(admin.username(), target.username(), AccountStatus.REJECTED);
        System.out.println("Account rejected: " + target.username() + " (ID " + target.id() + ")");
    }

    /**
     * Asks for a User ID until it is a number that exists in the pending list.
     * Entering 0 cancels and returns null.
     */
    private User askPendingUser(List<User> pending, String prompt) {
        while (true) {
            String input = in.readLine(prompt);
            int id;
            try {
                id = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number (0 to cancel).");
                continue;
            }
            if (id == 0) {
                System.out.println("Action cancelled.");
                return null;
            }
            for (User u : pending) {
                if (u.id() == id) return u;
            }
            System.out.println("ID " + id + " is not in the pending list. Try again (0 to cancel).");
        }
    }

    /* ---------------------------- UPDATE ROLE & STATUS ---------------------------- */

    private void updateUserRoleAndStatus(User admin) {
        List<User> users = userDao.getAllUsers();
        System.out.println("\n-- Update User Role & Status --");
        printUserTable(users);

        // 1. target User ID (0 cancels)
        int targetId = readIntInput("Enter the User ID to update (0 to cancel): ");
        if (targetId == 0) {
            System.out.println("Update cancelled.");
            return;
        }

        // Security rule: an admin may not modify the account they are logged in with
        if (targetId == admin.id()) {
            System.out.println("WARNING: Modifying the currently active admin account is restricted.");
            return;
        }

        User target = null;
        for (User u : users) {
            if (u.id() == targetId) target = u;
        }
        if (target == null) {
            System.out.println("No user found with ID " + targetId + ".");
            return;
        }

        // 2. new role (0 cancels)
        String newRole = readValidRole();
        if (newRole == null) {
            System.out.println("Update cancelled.");
            return;
        }
        // a Store Manager must belong to a branch, which this screen cannot assign
        if (newRole.equals(Role.STORE_MANAGER.name()) && target.branchName() == null) {
            System.out.println("Cannot make " + target.username() + " a STORE_MANAGER: no branch is assigned to this account.");
            return;
        }

        // 3. new status (0 cancels)
        String newStatus = readValidStatus();
        if (newStatus == null) {
            System.out.println("Update cancelled.");
            return;
        }

        if (service.updateUserRoleAndStatus(admin.username(), targetId, newRole, newStatus)) {
            System.out.println("Success! " + target.username() + " is now " + newRole + " / " + newStatus + ".");
        } else {
            System.out.println("Could not update that account.");
        }
    }

    /* ---------------------------- INPUT VALIDATION WRAPPERS ---------------------------- */

    /** Keeps asking until the input is a whole number, so letters/symbols can never crash the app. */
    private int readIntInput(String prompt) {
        while (true) {
            String input = in.readLine(prompt);
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    /** Keeps asking until a valid role is typed. Returns the role in UPPER CASE, or null if the admin enters 0. */
    private String readValidRole() {
        while (true) {
            String input = in.readLine("Enter new role (WAREHOUSE_STAFF, DELIVERY_DRIVER, STORE_MANAGER, SUPER_ADMIN, 0 to cancel): ")
                    .toUpperCase();
            if (input.equals("0")) return null;
            for (Role r : Role.values()) {
                if (r.name().equals(input)) return r.name();
            }
            System.out.println("Invalid role. Please choose one from the list.");
        }
    }

    /** Keeps asking until APPROVED or PENDING is typed. Returns it in UPPER CASE, or null if the admin enters 0. */
    private String readValidStatus() {
        while (true) {
            String input = in.readLine("Enter new status (APPROVED, PENDING, 0 to cancel): ").toUpperCase();
            if (input.equals("0")) return null;
            if (input.equals("APPROVED") || input.equals("PENDING")) return input;
            System.out.println("Invalid status. Please enter APPROVED or PENDING.");
        }
    }

    /** Prints any list of users as an aligned table (ID, Username, Role, Status). */
    private void printUserTable(List<User> users) {
        String border = "+-------+-----------------+------------------+------------+";
        String row = "| %-5s | %-15s | %-16s | %-10s |%n";

        System.out.println(border);
        System.out.printf(row, "ID", "Username", "Role", "Status");
        System.out.println(border);
        for (User u : users) {
            System.out.printf(row, u.id(), cut(u.username(), 15), u.role(), u.status());
        }
        System.out.println(border);
        System.out.println("Total: " + users.size());
    }

    /** Shortens text that is wider than its column so the table stays aligned. */
    private String cut(String text, int width) {
        return text.length() <= width ? text : text.substring(0, width - 3) + "...";
    }

    private void handleStockRequests(User admin) {
        List<StockRequest> pending = service.getPendingRequests();
        if (pending.isEmpty()) {
            System.out.println("No pending stock requests right now.");
            return;
        }
        System.out.println("\n-- Pending Stock Requests --");
        for (StockRequest r : pending) System.out.println("  " + r);

        int id = in.readPositiveInt("Enter Request # to review: ");
        System.out.println("1. Approve   2. Reject");
        String action = in.readLine("Choice: ");
        if (action.equals("1")) {
            boolean ok = service.approveRequest(admin.username(), id);
            System.out.println(ok ? "Request approved. A delivery job has been created."
                    : "Could not approve that request.");
        } else if (action.equals("2")) {
            boolean ok = service.rejectRequest(admin.username(), id);
            System.out.println(ok ? "Request rejected." : "Could not reject that request.");
        } else {
            System.out.println("Invalid action.");
        }
    }

    private void manageAccounts(User admin) {
        System.out.println("\n-- All Accounts --");
        for (User u : userDao.findAll()) System.out.println("  " + u);

        System.out.println("1. Block account   2. Unblock account   3. Delete account   4. Back");
        String action = in.readLine("Choice: ");
        if (action.equals("4")) return;
        if (!action.equals("1") && !action.equals("2") && !action.equals("3")) {
            System.out.println("Invalid action.");
            return;
        }

        String target = in.readLine("Enter username: ");
        User u = userDao.findByUsername(target);
        if (u == null) {
            System.out.println("Account not found.");
            return;
        }
        if (u.role() == Role.SUPER_ADMIN) {
            System.out.println("You cannot modify the SuperAdmin account.");
            return;
        }

        switch (action) {
            case "1" -> {
                service.setAccountStatus(admin.username(), u.username(), AccountStatus.BLOCKED);
                System.out.println("Account blocked: " + u.username());
            }
            case "2" -> {
                service.setAccountStatus(admin.username(), u.username(), AccountStatus.APPROVED);
                System.out.println("Account unblocked: " + u.username());
            }
            case "3" -> {
                service.deleteUser(admin.username(), u.username());
                System.out.println("Account deleted: " + u.username());
            }
        }
    }

    /* ---------------------------- SYSTEM AUDIT LOGS ---------------------------- */

    private void auditLogMenu(User admin) {
        boolean back = false;
        while (!back) {
            System.out.println("\n-- SYSTEM AUDIT LOGS --");
            System.out.println("1. View All System Logs");
            System.out.println("2. Filter Logs by User ID");
            System.out.println("3. Clear All Logs");
            System.out.println("0. Back");
            String choice = in.readLine("Choose an option: ");

            switch (choice) {
                case "1" -> {
                    List<ActivityLog> logs = service.getAllLogs();
                    System.out.println("\n-- All System Logs (newest first) --");
                    printLogTable(logs);
                }
                case "2" -> {
                    int userId = readIntInput("Enter the User ID to filter by (0 to cancel): ");
                    if (userId == 0) {
                        System.out.println("Filter cancelled.");
                    } else {
                        System.out.println("\n-- Logs for User ID " + userId + " --");
                        printLogTable(service.getLogsByUserId(userId));
                    }
                }
                case "3" -> clearLogs(admin);
                case "0" -> back = true;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    /** Clearing is permanent, so the admin has to type YES. The clearing itself is logged afterwards. */
    private void clearLogs(User admin) {
        String answer = in.readLine("This permanently deletes ALL log entries. Type YES to confirm (anything else cancels): ");
        if (!answer.equals("YES")) {
            System.out.println("Clear cancelled. Nothing was deleted.");
            return;
        }
        int removed = service.clearLogs(admin.username());
        System.out.println("Done. " + removed + " log entries were cleared.");
    }

    /** Aligned log table: Log ID | User ID | Action Type | Description | Timestamp. Long descriptions wrap. */
    private void printLogTable(List<ActivityLog> logs) {
        if (logs.isEmpty()) {
            System.out.println("No logs found.");
            return;
        }
        String border = "+" + "-".repeat(7) + "+" + "-".repeat(10) + "+" + "-".repeat(20) + "+"
                + "-".repeat(37) + "+" + "-".repeat(22) + "+";
        String row = "| %-5s | %-8s | %-18s | %-35s | %-20s |%n";

        System.out.println(border);
        System.out.printf(row, "ID", "User ID", "Action Type", "Description", "Timestamp");
        System.out.println(border);
        for (ActivityLog l : logs) {
            List<String> lines = wrap(l.description(), 35);
            System.out.printf(row, l.logId(), l.userId() == null ? "-" : l.userId(),
                    cut(l.actionType(), 18), lines.get(0), l.timestamp());
            for (int i = 1; i < lines.size(); i++) {
                System.out.printf(row, "", "", "", lines.get(i), "");
            }
        }
        System.out.println(border);
        System.out.println("Total: " + logs.size());
    }

    /** Splits text into lines of at most 'width' characters, breaking at spaces. */
    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > width) {
                if (cur.length() > 0) { lines.add(cur.toString()); cur.setLength(0); }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0 || lines.isEmpty()) lines.add(cur.toString());
        return lines;
    }
}