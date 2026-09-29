package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionRequest;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.mapper.TransactionMapper;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    private final AccountServiceClient accountServiceClient;

    private final TransactionMapper transactionMapper;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final RedisTemplate<String, String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED = "transaction.completed";
    private static final String TRANSACTION_REFUNDED = "transaction.refunded";
    private static final String FRAUD_DETECTED = "fraud.detected";

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

    public TransactionResponse verifyOtp(String transactionId, String otp) {
        log.info("Otp verification for the transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("No such transaction exists: "+ transactionId));

        String otpKey = "verification:otp" + transactionId;
        String storedOtp =redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null){
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP expired - transaction cancelled and amount refunded");
            return transactionMapper.toTransactionResponse(transaction);
        }
        else if(!storedOtp.equals(otp)){
            log.warn("Wrong OTP: blocking account and refunding: {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction, "Wrong OTP entered - transaction cancelled, amount refunded and account blocked for security");
            return transactionMapper.toTransactionResponse(transaction);
        }
        log.info("OTP verified. Completing transaction for: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return transactionMapper.toTransactionResponse(transaction);
    }

    private void completeTransaction(Transaction transaction) {
        //In this case, we need to deduct from the sender and credit the receiver
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedTime(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent transactionCompletedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNo(),
                transaction.getReceiverAccountNo(),
                transaction.getAmount(),
                transaction.getDescription()
        );

        kafkaTemplate.send(
                TRANSACTION_COMPLETED,
                transaction.getId(),
                transactionCompletedEvent
        );

        log.info("SAGA complete - Transaction {} completed", transaction.getId());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        //publish fraud.detected -> Account service will block account.
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNo());
        fraudEvent.put("reason", reason);

        kafkaTemplate.send(
                FRAUD_DETECTED, transaction.getSenderAccountNo(), fraudEvent
        );

        log.warn("fraud.detected published - account {} will be blocked. Kindly contact your bank.", transaction.getSenderAccountNo());

        //SAGA Compensation and refund sender
        compensateTransaction(transaction, reason);
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA compensation -  refunding: {}, amount: {}", transaction.getSenderAccountNo(), transaction.getAmount());

        //Credit money back to sender
        accountServiceClient.creditBalance(transaction.getSenderAccountNo(), transaction.getAmount());
        transaction.setTransactionStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason + " - SAGA Compensation executed," +
                "amount refunded at "+ LocalDateTime.now());

        transactionRepository.save(transaction);

        //PUBLISH refund event - Notification service will alert user

        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("accountNumber", transaction.getSenderAccountNo());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(
                TRANSACTION_REFUNDED, transaction.getId(), refundEvent
        );

        log.info("SAGS compensation complete - {} refunded to {}", transaction.getAmount(), transaction.getSenderAccountNo());
    }
}
