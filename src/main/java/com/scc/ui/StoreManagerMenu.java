package com.scc.ui;

import com.scc.dao.DatabaseException;
import com.scc.model.DeliveryJob;
import com.scc.model.StockRequest;
import com.scc.model.User;
import com.scc.service.CentralService;

import java.util.List;

public class StoreManagerMenu {

    private final CentralService service;
    private final ConsoleInput in;

    public StoreManagerMenu(CentralService service, ConsoleInput in) {
        this.service = service;
        this.in = in;
    }

    public void show(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("\n--- STORE/BRANCH MANAGER MENU (" + user.fullName() +
                    " - " + user.branchName() + ") ---");
            System.out.println("1. Request Rice (from central warehouse)");
            System.out.println("2. Swap Stock (request exchange from another branch)");
            System.out.println("3. Confirm Delivery (mark received)");
            System.out.println("4. View My Requests");
            System.out.println("5. View My Branch Stock Level");
            System.out.println("6. Logout");
            String choice = in.readLine("Choose an option: ");

            try {
                switch (choice) {
                    case "1" -> requestRice(user);
                    case "2" -> swapStock(user);
                    case "3" -> confirmDelivery(user);
                    case "4" -> viewMyRequests(user);
                    case "5" -> System.out.println("Current stock at " + user.branchName() + ": " +
                            service.getBranchStock(user.branchName()) + " kg");
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

    private void requestRice(User user) {
        int qty = in.readPositiveInt("Quantity to request (kg): ");
        StockRequest req = service.createRestockRequest(user.username(), user.branchName(), qty);
        System.out.println("Request submitted, awaiting admin approval: " + req);
    }

    private void swapStock(User user) {
        String sourceBranch = in.readLine("Which branch would you like to request an exchange from? ");
        int qty = in.readPositiveInt("Quantity to swap (kg): ");
        StockRequest req = service.createExchangeRequest(user.username(), user.branchName(), sourceBranch, qty);
        System.out.println("Exchange request submitted, awaiting admin approval: " + req);
    }

    private void confirmDelivery(User user) {
        List<DeliveryJob> jobs = service.getJobsAwaitingConfirmation(user.branchName());
        if (jobs.isEmpty()) {
            System.out.println("No deliveries waiting for your confirmation.");
            return;
        }
        for (DeliveryJob j : jobs) System.out.println("  " + j);
        int jobId = in.readPositiveInt("Enter Job # to confirm as received: ");
        boolean ok = service.confirmDeliveryByBranch(user.username(), jobId, user.branchName());
        System.out.println(ok ? "Delivery confirmed and stock updated!" : "Unable to confirm that job.");
    }

    private void viewMyRequests(User user) {
        List<StockRequest> reqs = service.getRequestsForBranch(user.branchName());
        if (reqs.isEmpty()) {
            System.out.println("You haven't made any requests yet.");
            return;
        }
        for (StockRequest r : reqs) System.out.println("  " + r);
    }
}