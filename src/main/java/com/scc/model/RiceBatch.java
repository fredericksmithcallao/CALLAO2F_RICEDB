package com.scc.model;

/** arrivalOrder is used for FIFO ordering (lower = older). */
public record RiceBatch(int id, String supplierName, int quantityKg,
                        int arrivalOrder, RiceStatus status) {

    @Override
    public String toString() {
        return "Batch #" + id + " | Supplier: " + supplierName + " | Qty: " + quantityKg +
                "kg | Arrival#: " + arrivalOrder + " | Status: " + status;
    }
}