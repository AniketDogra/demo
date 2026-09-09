package com.example.wallet.transfer.demo.model;

public class CreateWalletRequest {

    private Long initialBalancePaise;

    public CreateWalletRequest() {}

    public Long getInitialBalancePaise() { return initialBalancePaise; }
    public void setInitialBalancePaise(Long initialBalancePaise) { this.initialBalancePaise = initialBalancePaise; }

    public long effectiveBalance() {
        return initialBalancePaise != null ? initialBalancePaise : 0;
    }
}
