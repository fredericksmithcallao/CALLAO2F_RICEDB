package com.scc.ui;

import com.scc.dao.DatabaseException;
import com.scc.model.RiceBatch;
import com.scc.model.StockRequest;
import com.scc.model.User;
import com.scc.service.CentralService;

import java.util.List;

public class WarehouseMenu {

    private final CentralService service;
    private final ConsoleInput in;

    public WarehouseMenu(CentralService service, ConsoleInput in) {
        this.service = service;
        this.in = in;
    }

    public void show(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("\n--- WAREHOUSE STAFF MENU (" + user.fullName() + ") ---");
            System.out.println("1. Add Rice (log new arrivals)");
            System.out.println("2. Check Stock (view current inventory)");
            System.out.println("3. Report Damage (mark broken/wet/ruined bags)");
            System.out.println("4. View Approved Requests Awaiting Shipment");
            System.out.println("5. Logout");
            String choice = in.readLine("Choose an option: ");

            try {
                switch (choice) {
                    case "1" -> addRice(user);
                    case "2" -> checkStock();
                    case "3" -> reportDamage(user);
                    case "4" -> viewApprovedRequests();
                    case "5" -> {
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

    private void addRice(User user) {
        String supplier = in.readLine("Supplier name: ");
        if (supplier.isEmpty()) {
            System.out.println("Supplier name cannot be empty.");
            return;
        }
        int qty = in.readPositiveInt("Quantity received (kg): ");
        RiceBatch batch = service.addRiceBatch(user.username(), supplier, qty);
        System.out.println("Logged: " + batch);
    }

    private void checkStock() {
        List<RiceBatch> stock = service.getInStockBatches();
        if (stock.isEmpty()) {
            System.out.println("No rice currently in stock.");
            return;
        }
        System.out.println("Current stock (oldest first / FIFO order):");
        for (RiceBatch b : stock) {
            System.out.println("  " + b);
        }
        System.out.println("TOTAL IN STOCK: " + service.getTotalWarehouseStockKg() + " kg");
    }

    private void reportDamage(User user) {
        List<RiceBatch> stock = service.getInStockBatches();
        if (stock.isEmpty()) {
            System.out.println("No rice in stock to report damage on.");
            return;
        }
        for (RiceBatch b : stock) {
            System.out.println("  " + b);
        }
        int batchId = in.readPositiveInt("Enter Batch # to report damage: ");
        int damagedQty = in.readPositiveInt("Damaged quantity (kg): ");
        boolean ok = service.reportDamage(user.username(), batchId, damagedQty);
        System.out.println(ok ? "Damage reported and stock updated." : "Batch not found (or not in stock).");
    }

    private void viewApprovedRequests() {
        List<StockRequest> approved = service.getApprovedRequestsAwaitingShipment();
        if (approved.isEmpty()) {
            System.out.println("No approved requests waiting right now.");
            return;
        }
        for (StockRequest r : approved) {
            System.out.println("  " + r);
        }
    }
}