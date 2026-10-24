package com.scc;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.BranchStockDao;
import com.scc.dao.DatabaseConnection;
import com.scc.dao.DatabaseException;
import com.scc.dao.DatabaseInitializer;
import com.scc.dao.DeliveryJobDao;
import com.scc.dao.RiceBatchDao;
import com.scc.dao.StockRequestDao;
import com.scc.dao.UserDao;
import com.scc.service.CentralService;
import com.scc.ui.Authentication;
import com.scc.ui.ConsoleInput;

import java.util.Scanner;

/**
 * Startup entry point ONLY.
 *
 * Flow:  Initialize Database -> Instantiate DAOs & Utilities -> Delegate to Authentication.start()
 * Layers:  ui  ->  service  ->  dao  ->  SQLite database      (model = Java records shared by all layers)
 */
public class Main {

    public static void main(String[] args) {
        // 1. Initialize the database (tables + admin seed) before any menu appears
        try {
            new DatabaseInitializer().initialize();
        } catch (DatabaseException e) {
            System.out.println("Could not start the database: " + e.getMessage());
            return;
        }

        // 2. Instantiate the DAOs & utilities ONCE; the same instances are passed everywhere
        Scanner scanner = new Scanner(System.in);
        ConsoleInput input = new ConsoleInput(scanner);

        UserDao userDao = new UserDao();
        CentralService service = new CentralService(userDao, new RiceBatchDao(), new StockRequestDao(),
                new DeliveryJobDao(), new BranchStockDao(), new ActivityLogDao());

        // 3. Hand control to the authentication screen
        new Authentication(service, userDao, input).start();

        input.close();
        DatabaseConnection.close();
    }
}