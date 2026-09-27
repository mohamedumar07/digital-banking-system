package com.banking.accountservice.controller;

import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.dto.CreateAccountResponse;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

     @PostMapping
     public ResponseEntity<CreateAccountResponse> createAccount(
             @Valid @RequestBody CreateAccountRequest request
             ){
         return ResponseEntity.status(HttpStatus.CREATED)
                 .body(accountService.createAccount(request));
     }

     @GetMapping("/{accountNumber}")
     public ResponseEntity<CreateAccountResponse> getAccount(
            @PathVariable String accountNumber
     ){
         return ResponseEntity.ok()
                 .body(accountService.getAccount(accountNumber));
     }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable String accountNumber
    ){
        return ResponseEntity.ok()
                .body(accountService.getBalance(accountNumber));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(
            @PathVariable String accountNumber
    ){
         accountService.blockAccount(accountNumber);
         return ResponseEntity.ok()
                 .body("Account is blocked");
    }

    // Initiated by SAGA, when transfer is initiated
    //Step - 1
    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @RequestParam(name = "amount") BigDecimal amount
    ){
         accountService.deductBalance(accountNumber, amount);
         return ResponseEntity.ok(
                 "Balance deducted Successfully"
         );
    }

    //Step - 4
    // If fraud is detected, we need to refund to the sender.
    // else, the amount has to be sent to the receiver.
    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    ){
         accountService.creditBalance(accountNumber, amount);
         return ResponseEntity.ok("Balance credited successfully");
    }


}
