package com.meridianbank.ledger.web;

import com.meridianbank.ledger.service.LedgerPostingService;
import com.meridianbank.ledger.service.LedgerQueryService;
import com.meridianbank.ledger.web.dto.BalanceResponse;
import com.meridianbank.ledger.web.dto.LedgerEntryResponse;
import com.meridianbank.ledger.web.dto.PostingRequest;
import com.meridianbank.ledger.web.dto.PostingResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal-facing API — see the Trust Boundary note in ledger-service/README.md: authentication
 * is required, but resource ownership ("does this account belong to this customer") is not
 * checked here, since this service doesn't know that mapping by design. Callers (payment-service,
 * account-service) are expected to have already established ownership before calling.
 */
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerPostingService postingService;
    private final LedgerQueryService queryService;

    public LedgerController(LedgerPostingService postingService, LedgerQueryService queryService) {
        this.postingService = postingService;
        this.queryService = queryService;
    }

    @PostMapping("/postings")
    @ResponseStatus(HttpStatus.CREATED)
    public PostingResponse post(@Valid @RequestBody PostingRequest request) {
        return postingService.post(request.transactionId(), request.debitAccountId(), request.creditAccountId(),
                request.amount(), request.currency(), request.reference());
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable UUID accountId) {
        return BalanceResponse.from(queryService.getBalance(accountId));
    }

    @GetMapping("/accounts/{accountId}/entries")
    public Page<LedgerEntryResponse> getEntries(@PathVariable UUID accountId, Pageable pageable) {
        return queryService.getEntries(accountId, pageable).map(LedgerEntryResponse::from);
    }
}
