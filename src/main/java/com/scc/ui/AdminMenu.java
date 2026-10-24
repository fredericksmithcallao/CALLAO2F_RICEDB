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
            System.out.println("8. View Activity Log");
            System.out.println("9. Logout");
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
                    case "8" -> showActivityLog();
                    case "9" -> {
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

    private void showActivityLog() {
        List<ActivityLog> logs = service.getActivityLogs();
        System.out.println("\n-- Activity Log (newest first) --");
        if (logs.isEmpty()) {
            System.out.println("No activity recorded yet.");
            return;
        }
        for (ActivityLog l : logs) System.out.println("  " + l);
    }
}