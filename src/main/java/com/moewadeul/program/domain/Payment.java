package com.moewadeul.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_application_id")
    private long programApplicationId;

    private int amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static Payment paid(long programApplicationId, int amount, Instant now) {
        Payment payment = new Payment();
        payment.programApplicationId = programApplicationId;
        payment.amount = amount;
        payment.status = PaymentStatus.PAID;
        payment.paidAt = now;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void refund(Instant now) {
        if (status != PaymentStatus.PAID) {
            throw new IllegalStateException("결제 완료 상태의 결제만 환불할 수 있습니다.");
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = now;
        this.updatedAt = now;
    }
}
