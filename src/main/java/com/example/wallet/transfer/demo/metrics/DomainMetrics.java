package com.example.wallet.transfer.demo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DomainMetrics {

    private final Counter transfersCompleted;
    private final Counter transfersDeclined;
    private final Counter transfersReplayed;
    private final Counter walletsCreated;
    private final Counter walletsConflict;

    public DomainMetrics(MeterRegistry registry) {
        this.transfersCompleted = Counter.builder("wallet_transfers_total")
                .tag("result", "completed").register(registry);
        this.transfersDeclined = Counter.builder("wallet_transfers_total")
                .tag("result", "declined_insufficient_funds").register(registry);
        this.transfersReplayed = Counter.builder("wallet_transfers_total")
                .tag("result", "idempotent_replay").register(registry);
        this.walletsCreated = Counter.builder("wallet_wallets_total")
                .tag("result", "created").register(registry);
        this.walletsConflict = Counter.builder("wallet_wallets_total")
                .tag("result", "get_or_create_conflict").register(registry);
    }

    public void incrementTransfersCompleted()   { transfersCompleted.increment(); }
    public void incrementTransfersDeclined()     { transfersDeclined.increment(); }
    public void incrementTransfersReplayed()     { transfersReplayed.increment(); }
    public void incrementWalletsCreated()        { walletsCreated.increment(); }
    public void incrementWalletsConflict()       { walletsConflict.increment(); }
}
