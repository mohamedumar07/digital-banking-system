package com.banking.accountservice.mapper;

import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.dto.CreateAccountResponse;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

@Mapper
public interface AccountMapper {

    AccountMapper accountMapper = Mappers.getMapper(AccountMapper.class);

    @Mapping(target = "phoneNo", source = "account.phone")
    @Mapping(target = "balance", source = "account.initialDeposit")
    Account toEntity(CreateAccountRequest account);

    @AfterMapping
    default void setDefaultValues(@MappingTarget Account account){
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setTransactionLimit(
                account.getAccountType() == AccountType.SAVINGS ?
                        new BigDecimal("100000"): new BigDecimal("150000")
        );
    }

    CreateAccountResponse toResponse(Account account);
}
