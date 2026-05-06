package com.pki.ra.common.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Records inbound RA service requests for diagnostic and audit purposes.
 *
 * <p>Table: {@code request_log}
 * <ul>
 *   <li>{@code id}     — surrogate primary key</li>
 *   <li>{@code req}    — request description or action name</li>
 *   <li>{@code req_ts} — timestamp when the request was received</li>
 * </ul>
 */
@Entity
@Table(name = "request_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "req", nullable = false, length = 200)
    private String req;

    @Column(name = "req_ts", nullable = false)
    private LocalDateTime reqTs;
}
