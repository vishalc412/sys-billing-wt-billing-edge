package com.westfiled.api.billing.primaryAccount.mappers;

import com.westfiled.api.billing.primaryAccount.dtos.response.TransactionResponse;
import com.westfiled.api.billing.primaryAccount.entities.TransactionXml;
import org.springframework.stereotype.Component;

import java.util.List;

/** Replaces the mapTransactions/checkTransactionType DataWeave functions in transactions-implementation.xml. */
@Component
public class TransactionResponseMapper {

    public List<TransactionResponse> toResponseList(List<TransactionXml> transactions) {
        return transactions.stream().map(this::toResponse).toList();
    }

    public TransactionResponse toResponse(TransactionXml xml) {
        return TransactionResponse.builder()
                .amountDue(PrimaryAccountResponseMapper.round(xml.getAmountDue()))
                .description(xml.getDescription())
                .dueDate(xml.getDueDate())
                .processingDate(xml.getProcessingDate())
                .build();
    }
}
