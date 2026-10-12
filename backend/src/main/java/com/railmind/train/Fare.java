package com.railmind.train;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "fares")
public class Fare {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    @Column(name = "travel_class", nullable = false)
    private String travelClass;

    @Column(name = "base_fare", nullable = false)
    private BigDecimal baseFare;

    @Column(name = "per_km_rate", nullable = false)
    private BigDecimal perKmRate;
}
