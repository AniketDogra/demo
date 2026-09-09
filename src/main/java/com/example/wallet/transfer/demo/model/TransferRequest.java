package com.example.wallet.transfer.demo.model;

public class TransferRequest {

    private Long from;
    private Long to;
    private long amountPaise;
    private String idempotencyKey;

    public TransferRequest() {}

    public Long getFrom() { return from; }
    public void setFrom(Long from) { this.from = from; }

    public Long getTo() { return to; }
    public void setTo(Long to) { this.to = to; }

    public long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
