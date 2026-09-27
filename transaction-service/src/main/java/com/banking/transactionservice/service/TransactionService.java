package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionRequest;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.mapper.TransactionMapper;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    private final AccountServiceClient accountServiceClient;

    private final TransactionMapper transactionMapper;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED = "transaction.completed";
    private static final String TRANSACTION_REFUNDED = "transaction.refunded";

    /*
    * SAGA Step - 1: Initiates transfer
    * Deducts from sender via FeignClient, then saves transaction as PROCESSING
    * publish event to kafka for fraud check
    * */
    public TransactionResponse transfer(TransactionRequest transactionRequest) {
        log.info("SAGA start - Transfer from {} to {}, amount {}", transactionRequest.getSenderAccountNo(),
                transactionRequest.getReceiverAccountNo(), transactionRequest.getAmount());

        //SAGA step - 1: deduct from the sender
        accountServiceClient.deductBalance(transactionRequest.getSenderAccountNo(),
                transactionRequest.getAmount());
        Transaction transaction = transactionMapper.toTransaction(transactionRequest);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.PROCESSING);
        transaction.setReferenceNo(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as processing: {}", savedTransaction.getId());

        //SAGA Step-2: Transfer initiated event published
        TransactionInitiatedEvent transactionInitiatedEvent = transactionMapper.toTransactionInitiatedEvent(transaction);
        kafkaTemplate.send(
                TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), transactionInitiatedEvent
        );
        log.info("SAGA Step-2 - TransactionInitiatedEvent published for {}",
                transactionInitiatedEvent.getTransactionId());

        return transactionMapper.toTransactionResponse(savedTransaction);
    }

    public TransactionResponse getTransaction(String transactionId) {
        Optional<Transaction> transaction = transactionRepository.findById(transactionId);
        return transactionMapper.toTransactionResponse(transaction.orElseThrow(
                () -> new RuntimeException("Not a valid transaction id")
        ));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        List<Transaction> transactions = transactionRepository.findBySenderAccountNoAndOrderByCreatedAtDesc(accountNumber);
        return transactions.stream()
                .map(transactionMapper::toTransactionResponse)
                .collect(Collectors.toList());
    }
}
