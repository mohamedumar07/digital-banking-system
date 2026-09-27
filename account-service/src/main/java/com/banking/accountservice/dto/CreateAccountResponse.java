package com.banking.accountservice.dto;

import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateAccountResponse {

    private String id;

    private String accountNumber;

    private String accountHolderName;
    private String mobileNo;

    private String email;

    private AccountType accountType;

    private AccountStatus accountStatus;

    private BigDecimal balance;

    private BigDecimal transactionLimit;

    private LocalDateTime createdAt;

}
