package com.scc.model;

/** assignedDriver is null until a driver accepts the job. */
public record DeliveryJob(int id, int linkedRequestId, String pickupLocation, String dropLocation,
                          int quantityKg, String assignedDriver, DeliveryStatus status,
                          boolean signatureConfirmed) {

    @Override
    public String toString() {
        return "Job #" + id + " | " + pickupLocation + " -> " + dropLocation + " | Qty: " +
                quantityKg + "kg | Driver: " + (assignedDriver == null ? "UNASSIGNED" : assignedDriver) +
                " | Status: " + status + " | Signed: " + (signatureConfirmed ? "YES" : "NO");
    }
}