package com.scc.ui;

import com.scc.dao.DatabaseException;
import com.scc.model.DeliveryJob;
import com.scc.model.DeliveryStatus;
import com.scc.model.User;
import com.scc.service.CentralService;

import java.util.List;

public class DriverMenu {

    private final CentralService service;
    private final ConsoleInput in;

    public DriverMenu(CentralService service, ConsoleInput in) {
        this.service = service;
        this.in = in;
    }

    public void show(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("\n--- DELIVERY DRIVER MENU (" + user.fullName() + ") ---");
            System.out.println("1. View Available Jobs (unassigned)");
            System.out.println("2. Accept a Job");
            System.out.println("3. View My Jobs");
            System.out.println("4. Update Status (On the Way / Delivered)");
            System.out.println("5. Get Signature (confirm receiver signed)");
            System.out.println("6. Logout");
            String choice = in.readLine("Choose an option: ");

            try {
                switch (choice) {
                    case "1" -> viewAvailableJobs();
                    case "2" -> acceptJob(user);
                    case "3" -> viewMyJobs(user);
                    case "4" -> updateStatus(user);
                    case "5" -> confirmSignature(user);
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

    private void viewAvailableJobs() {
        List<DeliveryJob> jobs = service.getUnassignedJobs();
        if (jobs.isEmpty()) {
            System.out.println("No unassigned jobs right now.");
            return;
        }
        for (DeliveryJob j : jobs) System.out.println("  " + j);
    }

    private void acceptJob(User user) {
        List<DeliveryJob> jobs = service.getUnassignedJobs();
        if (jobs.isEmpty()) {
            System.out.println("No jobs available to accept.");
            return;
        }
        for (DeliveryJob j : jobs) System.out.println("  " + j);
        int jobId = in.readPositiveInt("Enter Job # to accept: ");
        DeliveryJob accepted = service.acceptJob(user.username(), jobId);
        System.out.println(accepted != null ? "Job accepted: " + accepted : "Job not found or already taken.");
    }

    private void viewMyJobs(User user) {
        List<DeliveryJob> jobs = service.getJobsForDriver(user.username());
        if (jobs.isEmpty()) {
            System.out.println("You have no jobs assigned.");
            return;
        }
        for (DeliveryJob j : jobs) System.out.println("  " + j);
    }

    private void updateStatus(User user) {
        List<DeliveryJob> jobs = service.getJobsForDriver(user.username());
        if (jobs.isEmpty()) {
            System.out.println("You have no jobs to update.");
            return;
        }
        for (DeliveryJob j : jobs) System.out.println("  " + j);
        int jobId = in.readPositiveInt("Enter Job # to update: ");
        System.out.println("1. On the Way   2. Delivered");
        String s = in.readLine("New status: ");
        if (s.equals("1")) {
            service.updateDeliveryStatus(user.username(), jobId, DeliveryStatus.ON_THE_WAY);
            System.out.println("Status updated to ON THE WAY.");
        } else if (s.equals("2")) {
            service.updateDeliveryStatus(user.username(), jobId, DeliveryStatus.DELIVERED);
            System.out.println("Status updated to DELIVERED. Please get the receiver's signature.");
        } else {
            System.out.println("Invalid status choice.");
        }
    }

    private void confirmSignature(User user) {
        int jobId = in.readPositiveInt("Enter Job # to confirm signature for: ");
        service.confirmSignature(user.username(), jobId);
        System.out.println("Signature captured. Delivery safely confirmed.");
    }
}