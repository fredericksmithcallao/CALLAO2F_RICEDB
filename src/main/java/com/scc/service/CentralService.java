package com.scc.service;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.BranchStockDao;
import com.scc.dao.DatabaseConnection;
import com.scc.dao.DeliveryJobDao;
import com.scc.dao.RiceBatchDao;
import com.scc.dao.StockRequestDao;
import com.scc.dao.UserDao;
import com.scc.model.AccountStatus;
import com.scc.model.ActivityLog;
import com.scc.model.DeliveryJob;
import com.scc.model.DeliveryStatus;
import com.scc.model.RequestStatus;
import com.scc.model.RequestType;
import com.scc.model.RiceBatch;
import com.scc.model.RiceStatus;
import com.scc.model.Role;
import com.scc.model.StockRequest;
import com.scc.model.User;

import java.util.List;
import java.util.function.Supplier;

/**
 * Business rules of the Centralized Rice Distribution and Exchange System.
 * (This is the old CentralSystem class -- the in-memory lists are replaced by DAO calls.)
 *
 * Rule violations are reported with IllegalArgumentException(message); the UI prints the message.
 * Every multi-step operation runs inside one database transaction, so it is all-or-nothing.
 */
public class CentralService {

    private final UserDao userDao = new UserDao();
    private final RiceBatchDao batchDao = new RiceBatchDao();
    private final StockRequestDao requestDao = new StockRequestDao();
    private final DeliveryJobDao jobDao = new DeliveryJobDao();
    private final BranchStockDao branchDao = new BranchStockDao();
    private final ActivityLogDao logDao = new ActivityLogDao();

    /* ---------------------------- TRANSACTION HELPERS ---------------------------- */

    private <T> T tx(Supplier<T> work) {
        DatabaseConnection.begin();
        try {
            T result = work.get();
            DatabaseConnection.commit();
            return result;
        } catch (RuntimeException e) {
            DatabaseConnection.rollback();
            throw e;
        }
    }

    private void txRun(Runnable work) {
        tx(() -> { work.run(); return null; });
    }

    /* ---------------------------- ACCOUNT / USER MANAGEMENT ---------------------------- */

    public boolean usernameExists(String username) {
        return userDao.findByUsername(username) != null;
    }

    /** New self-registered accounts always start PENDING until the admin approves them. */
    public User registerUser(String username, String password, String fullName, Role role, String branchName) {
        return tx(() -> {
            if (role == Role.STORE_MANAGER) {
                branchDao.ensureBranch(branchName);
            }
            User created = userDao.insert(new User(0, username, password, fullName, role,
                    AccountStatus.PENDING, role == Role.STORE_MANAGER ? branchName : null));
            logDao.insert(username, "ACCOUNT_REGISTERED", "New " + role + " account registered (PENDING).");
            return created;
        });
    }

    public User authenticate(String username, String password) {
        return userDao.authenticate(username, password);
    }

    public List<User> getUsers() { return userDao.findAll(); }

    public List<User> getPendingUsers() { return userDao.findByStatus(AccountStatus.PENDING); }

    public User findUserByUsername(String username) { return userDao.findByUsername(username); }

    /** Approve / reject / block / unblock (unblock = APPROVED). */
    public void setAccountStatus(String actor, String targetUsername, AccountStatus status) {
        txRun(() -> {
            userDao.updateStatus(targetUsername, status);
            logDao.insert(actor, "ACCOUNT_" + status, "Account '" + targetUsername + "' set to " + status + ".");
        });
    }

    public void deleteUser(String actor, String targetUsername) {
        txRun(() -> {
            logDao.insert(actor, "ACCOUNT_DELETED", "Account '" + targetUsername + "' was deleted.");
            userDao.delete(targetUsername);
        });
    }

    /* ---------------------------- WAREHOUSE STAFF ACTIONS ---------------------------- */

    public RiceBatch addRiceBatch(String actor, String supplier, int quantityKg) {
        return tx(() -> {
            RiceBatch batch = batchDao.insert(supplier, quantityKg);
            logDao.insert(actor, "RICE_ARRIVAL", "Batch #" + batch.id() + " logged: " + quantityKg +
                    "kg from " + supplier + ".");
            return batch;
        });
    }

    /** In-stock batches, oldest first (FIFO). */
    public List<RiceBatch> getInStockBatches() { return batchDao.findInStockFifo(); }

    public List<RiceBatch> getAllBatches() { return batchDao.findAll(); }

    public int getTotalWarehouseStockKg() { return batchDao.getTotalInStockKg(); }

    public boolean reportDamage(String actor, int batchId, int damagedQty) {
        return tx(() -> {
            RiceBatch b = batchDao.findById(batchId);
            if (b == null || b.status() != RiceStatus.IN_STOCK) {
                return false;
            }
            if (damagedQty >= b.quantityKg()) {
                // whole batch is damaged (quantity is kept so the audit trail shows how much was lost)
                batchDao.update(b.id(), b.quantityKg(), RiceStatus.DAMAGED);
            } else {
                batchDao.update(b.id(), b.quantityKg() - damagedQty, RiceStatus.IN_STOCK);
                // split off the damaged portion as its own record
                batchDao.insertDamagedPortion(b.supplierName(), damagedQty, b.arrivalOrder());
            }
            logDao.insert(actor, "DAMAGE_REPORTED", "Batch #" + batchId + ": " +
                    Math.min(damagedQty, b.quantityKg()) + "kg reported damaged.");
            return true;
        });
    }

    // FIFO shipping: pulls from the OLDEST in-stock batches first. Returns the kg actually shipped.
    private int shipRiceFIFO(int quantityNeeded) {
        int remaining = quantityNeeded;
        for (RiceBatch b : batchDao.findInStockFifo()) {
            if (remaining <= 0) break;
            if (b.quantityKg() <= remaining) {
                remaining -= b.quantityKg();
                batchDao.update(b.id(), 0, RiceStatus.SHIPPED);
            } else {
                batchDao.update(b.id(), b.quantityKg() - remaining, RiceStatus.IN_STOCK);
                remaining = 0;
            }
        }
        return quantityNeeded - remaining;
    }

    /* ---------------------------- STORE / BRANCH MANAGER ACTIONS ---------------------------- */

    public StockRequest createRestockRequest(String actor, String branchName, int quantityKg) {
        return tx(() -> {
            StockRequest req = requestDao.insert(branchName, null, RequestType.RESTOCK, quantityKg);
            logDao.insert(actor, "REQUEST_CREATED", "Restock request #" + req.id() + " for " +
                    quantityKg + "kg by " + branchName + ".");
            return req;
        });
    }

    public StockRequest createExchangeRequest(String actor, String requestingBranch,
                                              String sourceBranch, int quantityKg) {
        if (sourceBranch == null || sourceBranch.isBlank()) {
            throw new IllegalArgumentException("Please enter the branch you want to exchange with.");
        }
        if (sourceBranch.equalsIgnoreCase(requestingBranch)) {
            throw new IllegalArgumentException("You cannot request an exchange from your own branch.");
        }
        if (!branchDao.exists(sourceBranch)) {
            throw new IllegalArgumentException("Branch '" + sourceBranch + "' does not exist in the system.");
        }
        return tx(() -> {
            StockRequest req = requestDao.insert(requestingBranch, sourceBranch, RequestType.EXCHANGE, quantityKg);
            logDao.insert(actor, "REQUEST_CREATED", "Exchange request #" + req.id() + " for " + quantityKg +
                    "kg: " + requestingBranch + " <- " + sourceBranch + ".");
            return req;
        });
    }

    public List<StockRequest> getRequestsForBranch(String branchName) { return requestDao.findByBranch(branchName); }

    public List<StockRequest> getAllRequests() { return requestDao.findAll(); }

    public List<StockRequest> getPendingRequests() { return requestDao.findByStatus(RequestStatus.PENDING); }

    /** Requests the admin already approved that are on their way out of the warehouse / source branch. */
    public List<StockRequest> getApprovedRequestsAwaitingShipment() {
        return requestDao.findAll().stream()
                .filter(r -> r.status() == RequestStatus.APPROVED || r.status() == RequestStatus.SHIPPED)
                .toList();
    }

    public int getBranchStock(String branchName) { return branchDao.getStock(branchName); }

    /** DELIVERED jobs for this branch that it has not confirmed yet. */
    public List<DeliveryJob> getJobsAwaitingConfirmation(String branchName) {
        return jobDao.findAwaitingConfirmation(branchName);
    }

    public boolean confirmDeliveryByBranch(String actor, int jobId, String branchName) {
        return tx(() -> {
            DeliveryJob job = jobDao.findById(jobId);
            if (job == null || !job.dropLocation().equalsIgnoreCase(branchName)
                    || job.status() != DeliveryStatus.DELIVERED) {
                return false;
            }
            StockRequest linked = requestDao.findById(job.linkedRequestId());
            if (linked == null || linked.status() == RequestStatus.COMPLETED) {
                return false; // already confirmed -> never add the stock twice
            }

            branchDao.addStock(branchName, job.quantityKg());
            requestDao.updateStatus(linked.id(), RequestStatus.COMPLETED);
            // EXCHANGE: remove the shipped quantity from the source branch
            if (linked.type() == RequestType.EXCHANGE && linked.sourceBranch() != null) {
                branchDao.addStock(linked.sourceBranch(), -job.quantityKg());
            }
            logDao.insert(actor, "DELIVERY_COMPLETED", "Job #" + jobId + " received by " + branchName +
                    " (" + job.quantityKg() + "kg). Request #" + linked.id() + " COMPLETED.");
            return true;
        });
    }

    /* ---------------------------- DELIVERY DRIVER ACTIONS ---------------------------- */

    public List<DeliveryJob> getUnassignedJobs() { return jobDao.findByStatus(DeliveryStatus.UNASSIGNED); }

    public List<DeliveryJob> getJobsForDriver(String driverUsername) { return jobDao.findByDriver(driverUsername); }

    public List<DeliveryJob> getAllJobs() { return jobDao.findAll(); }

    public int countActiveDeliveries() { return jobDao.countActive(); }

    public DeliveryJob acceptJob(String driverUsername, int jobId) {
        return tx(() -> {
            DeliveryJob job = jobDao.findById(jobId);
            if (job == null || job.status() != DeliveryStatus.UNASSIGNED) {
                return null;
            }
            jobDao.assignDriver(jobId, driverUsername);
            logDao.insert(driverUsername, "JOB_ACCEPTED", "Job #" + jobId + " accepted.");
            return jobDao.findById(jobId);
        });
    }

    /** Flow: ASSIGNED -> ON_THE_WAY -> DELIVERED. */
    public void updateDeliveryStatus(String driverUsername, int jobId, DeliveryStatus newStatus) {
        txRun(() -> {
            DeliveryJob job = requireOwnJob(driverUsername, jobId);
            if (newStatus == DeliveryStatus.ON_THE_WAY && job.status() != DeliveryStatus.ASSIGNED) {
                throw new IllegalArgumentException("Only an ASSIGNED job can be set to ON THE WAY.");
            }
            if (newStatus == DeliveryStatus.DELIVERED && job.status() != DeliveryStatus.ON_THE_WAY) {
                throw new IllegalArgumentException("A job must be ON THE WAY before it can be marked DELIVERED.");
            }
            if (newStatus != DeliveryStatus.ON_THE_WAY && newStatus != DeliveryStatus.DELIVERED) {
                throw new IllegalArgumentException("Invalid status.");
            }
            jobDao.updateStatus(jobId, newStatus);
            logDao.insert(driverUsername, "STATUS_CHANGED", "Job #" + jobId + " is now " + newStatus + ".");
        });
    }

    public void confirmSignature(String driverUsername, int jobId) {
        txRun(() -> {
            DeliveryJob job = requireOwnJob(driverUsername, jobId);
            if (job.status() != DeliveryStatus.DELIVERED) {
                throw new IllegalArgumentException("Job must be marked DELIVERED before capturing a signature.");
            }
            jobDao.setSignatureConfirmed(jobId, true);
            logDao.insert(driverUsername, "SIGNATURE_CONFIRMED", "Receiver signature confirmed for Job #" + jobId + ".");
        });
    }

    private DeliveryJob requireOwnJob(String driverUsername, int jobId) {
        DeliveryJob job = jobDao.findById(jobId);
        if (job == null || !driverUsername.equalsIgnoreCase(job.assignedDriver())) {
            throw new IllegalArgumentException("Job not found or not assigned to you.");
        }
        return job;
    }

    /* ---------------------------- ADMIN: APPROVE STOCK TRANSFERS ---------------------------- */

    /**
     * Approving a request is what spawns the DeliveryJob the driver sees.
     * RESTOCK: needs enough warehouse stock, ships FIFO right away (status SHIPPED).
     * EXCHANGE: needs enough stock at the source branch (stock moves when the receiver confirms).
     */
    public boolean approveRequest(String actor, int requestId) {
        return tx(() -> {
            StockRequest req = requestDao.findById(requestId);
            if (req == null || req.status() != RequestStatus.PENDING) {
                return false;
            }

            if (req.type() == RequestType.RESTOCK) {
                if (batchDao.getTotalInStockKg() < req.quantityKg()) {
                    throw new IllegalArgumentException("Not enough rice in the warehouse (" +
                            batchDao.getTotalInStockKg() + " kg available, " + req.quantityKg() + " kg requested).");
                }
                shipRiceFIFO(req.quantityKg());
                requestDao.updateStatus(requestId, RequestStatus.SHIPPED);
                jobDao.insert(requestId, "WAREHOUSE", req.requestingBranch(), req.quantityKg());
            } else {
                if (branchDao.getStock(req.sourceBranch()) < req.quantityKg()) {
                    throw new IllegalArgumentException("Branch '" + req.sourceBranch() + "' only has " +
                            branchDao.getStock(req.sourceBranch()) + " kg, but " + req.quantityKg() + " kg was requested.");
                }
                requestDao.updateStatus(requestId, RequestStatus.APPROVED);
                jobDao.insert(requestId, req.sourceBranch(), req.requestingBranch(), req.quantityKg());
            }
            logDao.insert(actor, "REQUEST_APPROVED", "Request #" + requestId + " approved; delivery job created.");
            return true;
        });
    }

    public boolean rejectRequest(String actor, int requestId) {
        return tx(() -> {
            StockRequest req = requestDao.findById(requestId);
            if (req == null || req.status() != RequestStatus.PENDING) {
                return false;
            }
            requestDao.updateStatus(requestId, RequestStatus.REJECTED);
            logDao.insert(actor, "REQUEST_REJECTED", "Request #" + requestId + " rejected.");
            return true;
        });
    }

    /* ---------------------------- AUDIT LOG ---------------------------- */

    public List<ActivityLog> getActivityLogs() { return logDao.findAll(); }
}