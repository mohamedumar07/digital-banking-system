package com.banking.fraud_detection_service.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@AllArgsConstructor
public class FraudDetectionEventConsumer {

    private final FraudDetectionService fraudDetectionService;

    /*
     listens to transaction.initiated topic
     Every transaction goes through fraud check before completing
    */
    @KafkaListener(topics = "transaction.initiated", groupId = "fraud-detection-group")
    public void consumerTransactionInitiatedEvent(
            @Payload Map<String, Object> payload
    ){
        log.info("Received transaction for fraud check: {}", payload.get("transactionId"));
        try{
            fraudDetectionService.checkTransaction(payload);
        }catch (Exception e){

        }

    }
}
