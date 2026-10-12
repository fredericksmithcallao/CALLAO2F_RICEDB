package com.scc.ui;

import com.scc.dao.DatabaseException;
import com.scc.model.AccountStatus;
import com.scc.model.Role;
import com.scc.model.User;
import com.scc.service.CentralService;

/** Main menu: Login / Register / Exit. Sends each logged-in user to the menu of their role. */
public class MainMenu {

    private final CentralService service;
    private final ConsoleInput in;

    public MainMenu(CentralService service, ConsoleInput in) {
        this.service = service;
        this.in = in;
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("   CENTRALIZED RICE DISTRIBUTION AND EXCHANGE SYSTEM");
        System.out.println("=================================================================");

        boolean running = true;
        while (running) {
            System.out.println("\n---------------- MAIN MENU ----------------");
            System.out.println("1. Login");
            System.out.println("2. Register a new account");
            System.out.println("3. Exit");
            String choice = in.readLine("Choose an option: ");

            try {
                switch (choice) {
                    case "1" -> login();
                    case "2" -> register();
                    case "3" -> {
                        running = false;
                        System.out.println("Goodbye!");
                    }
                    default -> System.out.println("Invalid option. Please try again.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            } catch (DatabaseException e) {
                System.out.println("Database error: " + e.getMessage());
            }
        }
    }

    /* ---------------------------------- REGISTRATION ---------------------------------- */

    private void register() {
        System.out.println("\n---- ACCOUNT REGISTRATION ----");
        System.out.println("Note: your account will be PENDING until the System Boss/Admin approves it.");

        String username = in.readLine("Choose desired username: ");
        if (username.isEmpty() || service.usernameExists(username)) {
            System.out.println("That username is invalid or already taken.");
            return;
        }

        String password = in.readLine("Choose a password: ");
        if (password.isEmpty()) {
            System.out.println("Password cannot be empty.");
            return;
        }
        String fullName = in.readLine("Full name: ");
        if (fullName.isEmpty()) {
            System.out.println("Full name cannot be empty.");
            return;
        }

        System.out.println("Select your workfield / role:");
        System.out.println("1. Warehouse Staff (Stock Keeper)");
        System.out.println("2. Delivery Driver (Mover)");
        System.out.println("3. Store / Branch Manager (Receiver & Seller)");
        String roleChoice = in.readLine("Choose an option: ");

        Role role;
        String branchName = null;
        switch (roleChoice) {
            case "1" -> role = Role.WAREHOUSE_STAFF;
            case "2" -> role = Role.DELIVERY_DRIVER;
            case "3" -> {
                role = Role.STORE_MANAGER;
                branchName = in.readLine("Which branch/store do you work for? ");
                if (branchName.isEmpty()) {
                    System.out.println("Branch name cannot be empty. Registration cancelled.");
                    return;
                }
            }
            default -> {
                System.out.println("Invalid role selection. Registration cancelled.");
                return;
            }
        }

        service.registerUser(username, password, fullName, role, branchName);
        System.out.println("Registration submitted! Please wait for admin approval before logging in.");
    }

    /* ---------------------------------- LOGIN ---------------------------------- */

    private void login() {
        System.out.println("\n---- LOGIN ----");
        String username = in.readLine("Username: ");
        String password = in.readLine("Password: ");

        User user = service.authenticate(username, password);
        if (user == null) {
            System.out.println("Invalid username or password.");
            return;
        }
        if (user.status() == AccountStatus.PENDING) {
            System.out.println("Your account is still pending approval by the System Boss/Admin.");
            return;
        }
        if (user.status() == AccountStatus.REJECTED) {
            System.out.println("Your account registration was rejected.");
            return;
        }
        if (user.status() == AccountStatus.BLOCKED) {
            System.out.println("Your account has been blocked by the admin. Contact support.");
            return;
        }

        System.out.println("\nWelcome, " + user.fullName() + " (" + user.role() + ")!");

        switch (user.role()) {
            case SUPER_ADMIN -> new AdminMenu(service, in).show(user);
            case WAREHOUSE_STAFF -> new WarehouseMenu(service, in).show(user);
            case DELIVERY_DRIVER -> new DriverMenu(service, in).show(user);
            case STORE_MANAGER -> new StoreManagerMenu(service, in).show(user);
        }
    }
}