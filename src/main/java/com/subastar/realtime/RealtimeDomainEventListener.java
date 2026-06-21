package com.subastar.realtime;

import com.subastar.dto.realtime.AuctionRealtimeEvent;
import com.subastar.dto.realtime.RealtimeEventType;
import com.subastar.dto.realtime.UserNotificationRealtimeEvent;
import com.subastar.event.BidOutbidDomainEvent;
import com.subastar.event.BidPlacedDomainEvent;
import com.subastar.event.NotificationCreatedDomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RealtimeDomainEventListener {

    private final RealtimeEventPublisher realtimeEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidPlaced(BidPlacedDomainEvent event) {
        AuctionRealtimeEvent payload = AuctionRealtimeEvent.builder()
                .type(RealtimeEventType.BID_PLACED)
                .subastaId(event.subastaId())
                .itemId(event.itemId())
                .pujaId(event.pujaId())
                .monto(event.monto())
                .nombreUsuario(event.nombreUsuario())
                .emailUsuario(event.emailUsuario())
                .bidderEmail(event.emailUsuario())
                .timestamp(event.timestamp())
                .bestBid(event.bestBid())
                .minBid(event.minBid())
                .maxBid(event.maxBid())
                .pujaMinima(event.minBid())
                .pujaMaxima(event.maxBid())
                .secondsLeft(event.secondsLeft())
                .title("Nueva puja")
                .message("Se registro una nueva mejor oferta")
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
                .secondsLeft(event.secondsLeft())
                .build();
        realtimeEventPublisher.publishUserBidEvent(event.username(), payload);
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
