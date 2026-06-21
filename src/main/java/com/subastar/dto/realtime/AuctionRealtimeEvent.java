package com.subastar.dto.realtime;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AuctionRealtimeEvent {
    private RealtimeEventType type;
    private Integer subastaId;
    private Integer itemId;
    private Integer pujaId;
    private BigDecimal monto;
    private String nombreUsuario;
    private String emailUsuario;
    private String bidderEmail;
    private LocalDateTime timestamp;
    private BigDecimal pujaMinima;
    private BigDecimal pujaMaxima;
    private BigDecimal bestBid;
    private BigDecimal minBid;
    private BigDecimal maxBid;
    private Integer secondsLeft;
    private String message;
    private String title;
}
