package com.scc.ui;

import com.scc.dao.DatabaseException;
import com.scc.dao.UserDao;
import com.scc.model.AccountStatus;
import com.scc.model.Role;
import com.scc.model.User;
import com.scc.service.CentralService;

/**
 * Multi-role authentication screen: Login / Register / Exit.
 * Routes every logged-in user to the menu of their role.
 *
 * The single shared UserDao and ConsoleInput (one Scanner) created in Main are passed on to
 * every role menu, so nothing is created twice.
 */
public class Authentication {

    private final CentralService service;
    private final UserDao userDao;
    private final ConsoleInput in;

    public Authentication(CentralService service, UserDao userDao, ConsoleInput in) {
        this.service = service;
        this.userDao = userDao;
        this.in = in;
    }

    public void start() {
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
        if (username.isEmpty() || userDao.findByUsername(username) != null) {
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

        // all four target user roles from Section 2 of the approved System Proposal
        System.out.println("Select your workfield / role:");
        System.out.println("1. Warehouse Staff (Stock Keeper)");
        System.out.println("2. Delivery Driver (Mover)");
        System.out.println("3. Store / Branch Manager (Receiver & Seller)");
        System.out.println("4. System Admin (Overseer)");
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
            case "4" -> role = Role.SUPER_ADMIN;   // still PENDING until an existing admin approves it
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

        User user = userDao.authenticate(username, password);
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
        routeToMenu(user);
    }

    /** Sends the user straight to the menu that matches their role. */
    private void routeToMenu(User user) {
        switch (user.role()) {
            case SUPER_ADMIN -> new AdminMenu(service, userDao, in).show(user);
            case WAREHOUSE_STAFF -> new WarehouseMenu(service, userDao, in).show(user);
            case DELIVERY_DRIVER -> new DriverMenu(service, userDao, in).show(user);
            case STORE_MANAGER -> new StoreManagerMenu(service, userDao, in).show(user);
        }
    }
}