package com.subastar.realtime;

import com.subastar.dto.realtime.AuctionRealtimeEvent;
import com.subastar.dto.realtime.RealtimeEventType;
import com.subastar.dto.realtime.UserNotificationRealtimeEvent;
import com.subastar.event.BidOutbidDomainEvent;
import com.subastar.event.BidPlacedDomainEvent;
import com.subastar.event.NotificationCreatedDomainEvent;
import com.subastar.model.ItemCatalogo;
import com.subastar.repository.ItemCatalogoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class RealtimeDomainEventListener {

    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ItemCatalogoRepository itemCatalogoRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidPlaced(BidPlacedDomainEvent event) {
        BigDecimal pujaMinima = null;
        BigDecimal pujaMaxima = null;
        ItemCatalogo item = itemCatalogoRepository.findById(event.itemId()).orElse(null);
        if (item != null) {
            BigDecimal precioBase = item.getPrecioBase();
            BigDecimal mejorOferta = event.monto();
            pujaMinima = mejorOferta.add(precioBase.multiply(new BigDecimal("0.01")));
            pujaMaxima = mejorOferta.add(precioBase.multiply(new BigDecimal("0.20")));
        }
        AuctionRealtimeEvent payload = AuctionRealtimeEvent.builder()
                .type(RealtimeEventType.BID_PLACED)
                .subastaId(event.subastaId())
                .itemId(event.itemId())
                .pujaId(event.pujaId())
                .monto(event.monto())
                .nombreUsuario(event.nombreUsuario())
                .timestamp(event.timestamp())
                .pujaMinima(pujaMinima)
                .pujaMaxima(pujaMaxima)
                .build();
        realtimeEventPublisher.publishAuctionEvent(event.subastaId(), payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidOutbid(BidOutbidDomainEvent event) {
        AuctionRealtimeEvent payload = AuctionRealtimeEvent.builder()
                .type(RealtimeEventType.BID_OUTBID)
                .subastaId(event.subastaId())
                .itemId(event.itemId())
                .pujaId(event.pujaId())
                .monto(event.monto())
                .nombreUsuario(event.nombreUsuario())
                .timestamp(event.timestamp())
                .build();
        realtimeEventPublisher.publishUserBidEvent(event.username(), payload);
    }

    private BigDecimal calcularPujaMinima(BigDecimal precioBase, BigDecimal mejorOferta) {
        if (precioBase == null) return BigDecimal.ZERO;
        if (mejorOferta == null || mejorOferta.compareTo(BigDecimal.ZERO) <= 0) return precioBase;
        return mejorOferta.add(precioBase.multiply(BigDecimal.valueOf(0.01)));
    }

    private BigDecimal calcularPujaMaxima(BigDecimal precioBase, BigDecimal mejorOferta) {
        if (precioBase == null) return BigDecimal.ZERO;
        BigDecimal incrementoMaximo = precioBase.multiply(BigDecimal.valueOf(0.20));
        if (mejorOferta == null || mejorOferta.compareTo(BigDecimal.ZERO) <= 0) {
            return precioBase.add(incrementoMaximo);
        }
        return mejorOferta.add(incrementoMaximo);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedDomainEvent event) {
        UserNotificationRealtimeEvent payload = UserNotificationRealtimeEvent.builder()
                .type(RealtimeEventType.NOTIFICATION_CREATED)
                .notificationId(event.notificationId())
                .tipo(event.tipo())
                .titulo(event.titulo())
                .contenido(event.contenido())
                .timestamp(event.timestamp())
                .leido(event.leido())
                .build();
        realtimeEventPublisher.publishUserNotification(event.username(), payload);
    }
}
