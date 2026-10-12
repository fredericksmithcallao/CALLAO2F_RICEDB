package com.scc.ui;

import com.scc.dao.DatabaseException;
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
    private final ConsoleInput in;

    public AdminMenu(CentralService service, ConsoleInput in) {
        this.service = service;
        this.in = in;
    }

    public void show(User admin) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("\n--- SYSTEM BOSS/ADMIN MENU (" + admin.fullName() + ") ---");
            System.out.println("1. See Everything (dashboard)");
            System.out.println("2. Approve/Reject Pending Accounts");
            System.out.println("3. Approve/Reject Stock Transfer Requests");
            System.out.println("4. Manage Accounts (view / block / unblock / delete)");
            System.out.println("5. View Activity Log");
            System.out.println("6. Logout");
            String choice = in.readLine("Choose an option: ");

            try {
                switch (choice) {
                    case "1" -> showDashboard();
                    case "2" -> handlePendingAccounts(admin);
                    case "3" -> handleStockRequests(admin);
                    case "4" -> manageAccounts(admin);
                    case "5" -> showActivityLog();
                    case "6" -> {
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
        System.out.println("Total registered accounts      : " + service.getUsers().size());

        System.out.println("\n-- All Rice Batches --");
        for (RiceBatch b : service.getAllBatches()) System.out.println("  " + b);

        System.out.println("\n-- All Stock Requests --");
        for (StockRequest r : service.getAllRequests()) System.out.println("  " + r);

        System.out.println("\n-- All Delivery Jobs --");
        for (DeliveryJob j : service.getAllJobs()) System.out.println("  " + j);
        System.out.println("=============================================");
    }

    private void handlePendingAccounts(User admin) {
        List<User> pending = service.getPendingUsers();
        if (pending.isEmpty()) {
            System.out.println("No pending accounts right now.");
            return;
        }
        System.out.println("\n-- Pending Accounts --");
        for (User u : pending) System.out.println("  " + u);

        String target = in.readLine("Enter username to review (or blank to cancel): ");
        if (target.isEmpty()) return;

        User u = service.findUserByUsername(target);
        if (u == null || u.status() != AccountStatus.PENDING) {
            System.out.println("That account isn't pending.");
            return;
        }
        System.out.println("1. Approve   2. Reject");
        String action = in.readLine("Choice: ");
        if (action.equals("1")) {
            service.setAccountStatus(admin.username(), u.username(), AccountStatus.APPROVED);
            System.out.println("Account approved: " + u.username());
        } else if (action.equals("2")) {
            service.setAccountStatus(admin.username(), u.username(), AccountStatus.REJECTED);
            System.out.println("Account rejected: " + u.username());
        } else {
            System.out.println("Invalid action.");
        }
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
        for (User u : service.getUsers()) System.out.println("  " + u);

        System.out.println("1. Block account   2. Unblock account   3. Delete account   4. Back");
        String action = in.readLine("Choice: ");
        if (action.equals("4")) return;
        if (!action.equals("1") && !action.equals("2") && !action.equals("3")) {
            System.out.println("Invalid action.");
            return;
        }

        String target = in.readLine("Enter username: ");
        User u = service.findUserByUsername(target);
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