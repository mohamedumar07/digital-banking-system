package com.banking.transactionservice.mapper;

import com.banking.transactionservice.dto.TransactionRequest;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface TransactionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "transactionStatus", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "referenceNo", ignore = true)
    @Mapping(target = "createdTime", ignore = true)
    @Mapping(target = "completedTime", ignore = true)
    Transaction toTransaction(TransactionRequest transactionRequest);

    @Mapping(target = "transactionId", source = "id")
    TransactionInitiatedEvent toTransactionInitiatedEvent(Transaction transaction);

    TransactionResponse toTransactionResponse(Transaction transaction);
}
