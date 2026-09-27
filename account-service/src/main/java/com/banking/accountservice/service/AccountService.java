package com.banking.accountservice.service;

import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.dto.CreateAccountResponse;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.mapper.AccountMapper;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    private final AccountMapper accountMapper;

    private static final SecureRandom secureRandom = new SecureRandom();
    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for {}", request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account already exists for email: "+ request.getEmail());
        }else{
            Account account = accountMapper.toEntity(request);
            account.setAccountNumber(generateAccountNumber());
            accountRepository.save(account);
            log.info("Account created: {}", account.getAccountNumber());
            return accountMapper.toResponse(account);
        }
    }

    //Generates unique 12 digit number by using SecureRandom and checking in the DB.
    private String generateAccountNumber(){
        String accountNumber = null;

        do{
            accountNumber = String.format("%012d",secureRandom.nextLong(1_000_000_000_000L));
        }while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    public CreateAccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number is not registered."));
        return accountMapper.toResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number is not registered."));
        return account.getBalance();
    }

    //Block Account -> This is called by Fraud Detection Service through Kafka
    public void blockAccount(String accountNumber) {
        log.info("Blocking account {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number not found."));
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked: {}", account.getAccountNumber());
    }

    //Deduct balance from the sender account
    //Called by Transaction service
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting balance from {} of sum {}", accountNumber, amount);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number not found."));

        if(account.getAccountStatus() != AccountStatus.ACTIVE){
            throw new RuntimeException("Account not active" + accountNumber);
        }

        if(account.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Insufficient balance for account: "+ accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance updated. New balance {}", account.getBalance()) ;
    }

    //Credit balance to the receiver, when called by transaction service
    public void creditBalance(String accountNumber, BigDecimal amount){
        log.info("Crediting balance to {} of sum {}", accountNumber, amount);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account number not found."));

        if(account.getAccountStatus() != AccountStatus.ACTIVE){
            throw new RuntimeException("Receiver account is not active" + accountNumber);
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Account credited to {}", accountNumber);
    }
}
