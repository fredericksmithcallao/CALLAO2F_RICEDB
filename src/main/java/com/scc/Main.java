package com.scc;

import com.scc.dao.DatabaseConnection;
import com.scc.dao.DatabaseException;
import com.scc.service.CentralService;
import com.scc.ui.ConsoleInput;
import com.scc.ui.MainMenu;

import java.util.Scanner;

/**
 * App entry point.
 *
 * Layers:  ui  ->  service  ->  dao  ->  SQLite database      (model = Java records shared by all layers)
 */
public class Main {

    public static void main(String[] args) {
        try {
            DatabaseConnection.initialize();   // creates tables + seeds the admin account (first run only)
        } catch (DatabaseException e) {
            System.out.println("Could not start the database: " + e.getMessage());
            return;
        }

        ConsoleInput input = new ConsoleInput(new Scanner(System.in));
        new MainMenu(new CentralService(), input).run();

        input.close();
        DatabaseConnection.close();
    }
}