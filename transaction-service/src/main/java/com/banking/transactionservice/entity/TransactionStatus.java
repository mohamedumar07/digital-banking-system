package com.banking.transactionservice.entity;

public enum TransactionStatus {
    /*
    * PENDING_VERIFICATION -> when the fraud is detected, but we have stopped it until the sender confirms
    * FLAGGED -> Upon the verification failure
    * */

    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED
}
