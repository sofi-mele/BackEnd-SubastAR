package com.subastar.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BidPlacedDomainEvent(
        Integer subastaId,
        Integer itemId,
        Integer pujaId,
        BigDecimal monto,
        String nombreUsuario,
        String emailUsuario,
        LocalDateTime timestamp,
        BigDecimal bestBid,
        BigDecimal minBid,
        BigDecimal maxBid,
        Integer secondsLeft
) {
}
