package com.banking.transactionservice.service;

import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Service
@Slf4j
public class TransactionEventConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final RedisTemplate<String, String> redisTemplate;

    private final TransactionRepository transactionRepository;
    private static final long OTP_EXPIRY_MINUTES = 5;

    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";

    /*
    * Consumes verification required event
    * and asks user to verify via OTP
    * */
    @KafkaListener(topics = "verification.required")
    public void consumeVerificationInitiatedEvent(
            @Payload Map<String, Object> payload){
        try{
            String transactionId = (String)payload.get("transactionId");
            String accountNumber = (String)payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            log.info("Verification required for {} -  Transaction: {}, " +
                    "Reason: {}", accountNumber, transactionId, reason);

            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() ->
                            new RuntimeException("No such transaction exists."));

            if(transaction.getTransactionStatus() != TransactionStatus.PROCESSING){
                log.warn("Transaction {} not processing - skipping", transactionId);
                return;
            }

            //Generate 6 digit OTP
            String otp = String.format("%06d", (int)(Math.random() * 900000) + 100000);

            //Store OTP in redis
            String otpKey = "verification:otp" + transactionId;

            redisTemplate.opsForValue().set(otpKey, otp, 5, TimeUnit.MINUTES);

            //Update Status
            transaction.setTransactionStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP generated for transaction: {} expires in {} min", transactionId, OTP_EXPIRY_MINUTES);

            //Notify User
            Map<String, Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId", transactionId);
            otpEvent.put("accountNumber", accountNumber);
            otpEvent.put("reason", reason);
            otpEvent.put("otp", otp);
            otpEvent.put("amount", payload.get("amount"));

            kafkaTemplate.send(
                    TRANSACTION_OTP_GENERATED_TOPIC, transactionId, otpEvent
            );
        }catch (Exception e){
            log.error("Error handling verification required: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumerFraudCheckCleanResult(
            @Payload Map<String, Object> payload
    ){
        try{

        }
    }
}
