package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    //Consumes transaction completed from kafka
    // credits the receiver account
    @KafkaListener(topics = "transaction.completed")
    public void consumerTransactionCompleted(
            @Payload Map<String, Object> payload){

        try{
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            log.info("Crediting account: {}, amount: {}", amount, receiverAccount);

            accountService.creditBalance(receiverAccount, amount);
        }catch (Exception e){
            log.error("Error crediting account: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.detected")
    //if the fraud is detected from the fraud service, block the account
    //the event is recieved from Kafka
    public void consumeFraudDetected(@Payload Map<String, Object> payload){

        try {
            String accountNumber = (String) payload.get("accountNumber");
            log.info("Fraud detected. Blocking the account {}.", accountNumber);
            accountService.blockAccount(accountNumber);
        }catch (Exception e){
            log.info("Error blocking account: " +e.getMessage());
        }

    }

}
