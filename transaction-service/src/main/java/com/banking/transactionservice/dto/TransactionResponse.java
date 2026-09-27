package com.banking.transactionservice.dto;

import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {
    private String id;

    private String senderAccountNo;

    private String receiverAccountNo;

    private BigDecimal amount;

    private TransactionType transactionType;

    private TransactionStatus transactionStatus;

    private String description;

    private String failureReason;

    private String referenceNo;

    private LocalDateTime createdTime;

    private LocalDateTime completedTime;
}
