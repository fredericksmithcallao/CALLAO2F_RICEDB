package com.scc.model;

/** sourceBranch is only used for EXCHANGE requests (null for RESTOCK). */
public record StockRequest(int id, String requestingBranch, String sourceBranch,
                           RequestType type, int quantityKg, RequestStatus status) {

    @Override
    public String toString() {
        String src = (type == RequestType.EXCHANGE) ? (" | From branch: " + sourceBranch) : " | From: WAREHOUSE";
        return "Request #" + id + " | Type: " + type + " | Branch: " + requestingBranch +
                src + " | Qty: " + quantityKg + "kg | Status: " + status;
    }
}