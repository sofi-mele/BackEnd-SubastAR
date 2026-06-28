package com.subastar.service;

import com.subastar.dto.realtime.AuctionRealtimeEvent;
import com.subastar.dto.realtime.RealtimeEventType;
import com.subastar.model.Catalogo;
import com.subastar.model.Cliente;
import com.subastar.model.CompraExtra;
import com.subastar.model.Duenio;
import com.subastar.model.ItemCatalogo;
import com.subastar.model.Pujo;
import com.subastar.model.PujoExtra;
import com.subastar.model.RegistroDeSubasta;
import com.subastar.model.Subasta;
import com.subastar.model.SubastaExtra;
import com.subastar.realtime.RealtimeEventPublisher;
import com.subastar.repository.CatalogoRepository;
import com.subastar.repository.ClienteRepository;
import com.subastar.repository.CompraExtraRepository;
import com.subastar.repository.DuenioRepository;
import com.subastar.repository.ItemCatalogoRepository;
import com.subastar.repository.PujoExtraRepository;
import com.subastar.repository.PujoRepository;
import com.subastar.repository.ProductoDetalleRepository;
import com.subastar.repository.ProductoRepository;
import com.subastar.repository.RegistroDeSubastaRepository;
import com.subastar.repository.SubastaExtraRepository;
import com.subastar.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubastaEnVivoTimerService {

    private final SubastaExtraRepository subastaExtraRepository;
    private final SubastaRepository subastaRepository;
    private final CatalogoRepository catalogoRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final PujoRepository pujoRepository;
    private final PujoExtraRepository pujoExtraRepository;
    private final RegistroDeSubastaRepository registroDeSubastaRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final ClienteRepository clienteRepository;
    private final DuenioRepository duenioRepository;
    private final ProductoRepository productoRepository;
    private final ProductoDetalleRepository productoDetalleRepository;
    private final NotificacionService notificacionService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final TransactionTemplate transactionTemplate;

    private final ConcurrentHashMap<Integer, AuctionTimerState> timers = new ConcurrentHashMap<>();

    @Value("${app.auction.initial-seconds:60}")
    private int initialSeconds;

    @Value("${app.auction.bid-extension-seconds:30}")
    private int bidExtensionSeconds;

    @Value("${subastar.empresa.cliente-id:}")
    private String empresaClienteId;

    public Integer ensureTimerStarted(Integer subastaId) {
        TimerInitialization initialization = transactionTemplate.execute(status -> initializeCurrentItem(subastaId));
        if (initialization == null || initialization.itemId() == null) {
            timers.remove(subastaId);
            if (initialization != null && initialization.auctionFinished()) {
                publishAuctionFinished(subastaId);
            }
            return null;
        }

        AtomicBoolean started = new AtomicBoolean(false);
        AuctionTimerState state = timers.compute(subastaId, (id, current) -> {
            if (current != null && current.itemId().equals(initialization.itemId())) {
                return current;
            }
            started.set(true);
            return new AuctionTimerState(initialization.itemId(), Instant.now().plusSeconds(initialSeconds));
        });

        if (started.get()) {
            log.info("Timer iniciado subasta={} item={} segundos={}",
                    subastaId, initialization.itemId(), initialSeconds);
        }
        return secondsLeft(state);
    }

    public int extendAfterBid(Integer subastaId, Integer itemId) {
        AtomicReference<AuctionTimerState> previous = new AtomicReference<>();
        AuctionTimerState extended = new AuctionTimerState(itemId, Instant.now().plusSeconds(bidExtensionSeconds));

        timers.compute(subastaId, (id, current) -> {
            previous.set(current);
            return extended;
        });

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        timers.compute(subastaId, (id, current) ->
                                extended.equals(current) ? previous.get() : current);
                    }
                }
            });
        }

        log.info("Timer extendido por puja subasta={} item={} segundos={}",
                subastaId, itemId, bidExtensionSeconds);
        return secondsLeft(extended);
    }

    public Integer getSecondsLeft(Integer subastaId) {
        AuctionTimerState state = timers.get(subastaId);
        return state != null ? secondsLeft(state) : null;
    }

    public void resetTimer(Integer subastaId) {
        timers.remove(subastaId);
        log.info("Timer reseteado en memoria para subasta={}", subastaId);
    }

    @Scheduled(fixedDelayString = "${app.auction.tick-ms:1000}")
    public void handleExpiredTimers() {
        Instant now = Instant.now();
        timers.forEach((subastaId, state) -> {
            if (!state.deadline().isAfter(now)) {
                try {
                    finalizeCurrentLotAndAdvance(subastaId);
                } catch (Exception ex) {
                    log.error("Error al cerrar lote vencido subasta={} item={}: {}",
                            subastaId, state.itemId(), ex.getMessage(), ex);
                }
            }
        });
    }

    public void finalizeCurrentLotAndAdvance(Integer subastaId) {
        AuctionTimerState expected = timers.get(subastaId);
        if (expected == null || expected.deadline().isAfter(Instant.now())) {
            return;
        }

        LotClosureResult result = transactionTemplate.execute(status -> closeExpiredLot(subastaId, expected));
        if (result == null) {
            return;
        }

        if (result.nextItem() != null) {
            AuctionTimerState nextState = new AuctionTimerState(
                    result.nextItem().getIdentificador(), Instant.now().plusSeconds(initialSeconds));
            timers.compute(subastaId, (id, current) -> {
                if (current != null && current.itemId().equals(result.nextItem().getIdentificador())) {
                    return current;
                }
                return nextState;
            });
            publishLotChanged(subastaId, result.nextItem(), secondsLeft(timers.get(subastaId)));
            log.info("Avance de lote subasta={} item_anterior={} siguiente_item={}",
                    subastaId, result.closedItemId(), result.nextItem().getIdentificador());
        } else {
            timers.remove(subastaId, expected);
            publishAuctionFinished(subastaId);
            log.info("Fin de subasta={} sin mas lotes disponibles", subastaId);
        }
    }

    private TimerInitialization initializeCurrentItem(Integer subastaId) {
        Subasta subasta = subastaRepository.findById(subastaId).orElse(null);
        if (subasta == null || !"abierta".equals(subasta.getEstado())) {
            return null;
        }

        SubastaExtra extra = subastaExtraRepository.findBySubastaIdWithLock(subastaId).orElse(null);
        if (extra == null) {
            return null;
        }

        ItemCatalogo current = extra.getItemActualId() != null
                ? itemCatalogoRepository.findById(extra.getItemActualId()).orElse(null)
                : null;
        if (current == null || "si".equals(current.getSubastado())) {
            current = findFirstAvailableItem(subastaId);
            if (current == null) {
                subasta.setEstado("cerrada");
                subastaRepository.save(subasta);
                extra.setItemActualId(null);
                extra.setFechaInicioLote(null);
                extra.setFechaUltimaPuja(null);
                subastaExtraRepository.save(extra);
                return new TimerInitialization(null, true);
            }
            extra.setItemActualId(current.getIdentificador());
            extra.setFechaInicioLote(LocalDateTime.now());
            extra.setFechaUltimaPuja(null);
            subastaExtraRepository.save(extra);
        } else if (extra.getFechaInicioLote() == null) {
            extra.setFechaInicioLote(LocalDateTime.now());
            extra.setFechaUltimaPuja(null);
            subastaExtraRepository.save(extra);
        }

        return new TimerInitialization(current.getIdentificador(), false);
    }

    private LotClosureResult closeExpiredLot(Integer subastaId, AuctionTimerState expected) {
        SubastaExtra extra = subastaExtraRepository.findBySubastaIdWithLock(subastaId).orElse(null);
        if (extra == null || !Objects.equals(extra.getItemActualId(), expected.itemId())) {
            return null;
        }

        AuctionTimerState currentState = timers.get(subastaId);
        if (currentState == null
                || !currentState.itemId().equals(expected.itemId())
                || currentState.deadline().isAfter(Instant.now())) {
            return null;
        }

        Subasta subasta = subastaRepository.findById(subastaId).orElse(null);
        ItemCatalogo item = itemCatalogoRepository.findById(expected.itemId()).orElse(null);
        if (subasta == null || item == null || !"abierta".equals(subasta.getEstado())) {
            return null;
        }

        List<Pujo> bids = pujoRepository.findByItemIdentificadorOrderByIdentificadorDesc(item.getIdentificador());
        bids.forEach(bid -> bid.setGanador("no"));
        Pujo winner = bids.stream()
                .max(Comparator.comparing(Pujo::getImporte)
                        .thenComparing(Pujo::getIdentificador, Comparator.reverseOrder()))
                .orElse(null);

        if (winner != null) {
            winner.setGanador("si");
            pujoRepository.saveAll(bids);
            Integer medioPagoId = pujoExtraRepository.findByPujoId(winner.getIdentificador())
                    .map(PujoExtra::getMedioPagoId)
                    .orElse(null);
            createPurchase(subasta, item, winner.getAsistente().getCliente(), winner.getImporte(), medioPagoId);
            transferOwnership(item, winner.getAsistente().getCliente());
            notifyWinner(item, winner);
            log.info("Ganador elegido subasta={} item={} puja={} cliente={} importe={}",
                    subastaId, item.getIdentificador(), winner.getIdentificador(),
                    winner.getAsistente().getCliente().getIdentificador(), winner.getImporte());
        } else {
            createCompanyPurchase(subasta, item);
            log.info("Lote cerrado sin pujas subasta={} item={}", subastaId, item.getIdentificador());
        }

        item.setSubastado("si");
        itemCatalogoRepository.save(item);
        log.info("Lote cerrado subasta={} item={}", subastaId, item.getIdentificador());

        ItemCatalogo next = findFirstAvailableItem(subastaId);
        if (next != null) {
            extra.setItemActualId(next.getIdentificador());
            extra.setFechaInicioLote(LocalDateTime.now());
            extra.setFechaUltimaPuja(null);
        } else {
            subasta.setEstado("cerrada");
            subastaRepository.save(subasta);
            extra.setItemActualId(null);
            extra.setFechaInicioLote(null);
            extra.setFechaUltimaPuja(null);
        }
        subastaExtraRepository.save(extra);

        return new LotClosureResult(item.getIdentificador(), next);
    }

    private ItemCatalogo findFirstAvailableItem(Integer subastaId) {
        return catalogoRepository.findAllBySubastaIdentificador(subastaId).stream()
                .map(Catalogo::getIdentificador)
                .flatMap(catalogoId -> itemCatalogoRepository.findByCatalogoIdentificador(catalogoId).stream())
                .filter(item -> !"si".equals(item.getSubastado()))
                .min(Comparator.comparing(ItemCatalogo::getIdentificador))
                .orElse(null);
    }

    private void createPurchase(Subasta subasta, ItemCatalogo item, Cliente buyer,
                                BigDecimal amount, Integer medioPagoId) {
        RegistroDeSubasta registro = new RegistroDeSubasta();
        registro.setSubasta(subasta);
        registro.setDuenio(item.getProducto().getDuenio());
        registro.setProducto(item.getProducto());
        registro.setCliente(buyer);
        registro.setImporte(amount);
        registro.setComision(item.getComision());
        registro = registroDeSubastaRepository.save(registro);

        CompraExtra compraExtra = new CompraExtra();
        compraExtra.setRegistroId(registro.getIdentificador());
        if (medioPagoId != null) {
            compraExtra.setMedioPagoId(medioPagoId);
        }
        compraExtraRepository.save(compraExtra);
    }

    private void createCompanyPurchase(Subasta subasta, ItemCatalogo item) {
        if (empresaClienteId == null || empresaClienteId.isBlank()) {
            log.error("Lote sin pujas subasta={} item={}: falta configurar SUBASTAR_EMPRESA_CLIENTE_ID",
                    subasta.getIdentificador(), item.getIdentificador());
            return;
        }

        final Integer clienteId;
        try {
            clienteId = Integer.valueOf(empresaClienteId.trim());
        } catch (NumberFormatException ex) {
            log.error("Lote sin pujas subasta={} item={}: SUBASTAR_EMPRESA_CLIENTE_ID no es un entero valido",
                    subasta.getIdentificador(), item.getIdentificador());
            return;
        }

        Cliente empresa = clienteRepository.findById(clienteId).orElse(null);
        if (empresa == null) {
            log.error("Lote sin pujas subasta={} item={}: cliente empresa id={} no encontrado",
                    subasta.getIdentificador(), item.getIdentificador(), clienteId);
            return;
        }

        // La empresa paga el precio base al instante: la compra NO debe quedar
        // pendiente (si no, el MultaScheduler la multaría a las 72hs).
        RegistroDeSubasta registro = new RegistroDeSubasta();
        registro.setSubasta(subasta);
        registro.setDuenio(item.getProducto().getDuenio());
        registro.setProducto(item.getProducto());
        registro.setCliente(empresa);
        registro.setImporte(item.getPrecioBase());
        registro.setComision(item.getComision());
        registro = registroDeSubastaRepository.save(registro);

        CompraExtra compraExtra = new CompraExtra();
        compraExtra.setRegistroId(registro.getIdentificador());
        compraExtra.setEstadoPago("pagado");
        compraExtraRepository.save(compraExtra);
        transferOwnership(item, empresa);
        log.info("Auto-compra empresa registrada subasta={} item={} cliente={} importe={}",
                subasta.getIdentificador(), item.getIdentificador(), clienteId, item.getPrecioBase());
    }

    private void transferOwnership(ItemCatalogo item, Cliente buyer) {
        Duenio nuevoDuenio = duenioRepository.findById(buyer.getIdentificador()).orElse(null);
        if (nuevoDuenio == null) {
            log.warn("No se actualizo duenio del producto={} porque cliente={} no tiene registro Duenio",
                    item.getProducto().getIdentificador(), buyer.getIdentificador());
            return;
        }

        item.getProducto().setDuenio(nuevoDuenio);
        productoRepository.save(item.getProducto());
        log.info("Duenio actualizado producto={} nuevo_duenio={}",
                item.getProducto().getIdentificador(), nuevoDuenio.getIdentificador());
    }

    private void notifyWinner(ItemCatalogo item, Pujo winner) {
        String itemName = item.getProducto().getDescripcionCatalogo() != null
                ? item.getProducto().getDescripcionCatalogo()
                : "Item #" + item.getIdentificador();
        BigDecimal costoEnvio = productoDetalleRepository
                .findById(item.getProducto().getIdentificador())
                .map(det -> det.getCostoEnvio())
                .orElse(null);
        notificacionService.notificarGanadorSubasta(
                winner.getAsistente().getCliente(), itemName, winner.getImporte(), item.getComision(), costoEnvio);
    }

    private void publishLotChanged(Integer subastaId, ItemCatalogo item, int secondsLeft) {
        BigDecimal base = item.getPrecioBase();
        BigDecimal minBid = base != null ? base.add(base.multiply(new BigDecimal("0.01"))) : null;
        AuctionRealtimeEvent event = AuctionRealtimeEvent.builder()
                .type(RealtimeEventType.LOT_CHANGED)
                .subastaId(subastaId)
                .itemId(item.getIdentificador())
                .timestamp(LocalDateTime.now())
                .bestBid(BigDecimal.ZERO)
                .minBid(minBid)
                .maxBid(base != null ? base.multiply(new BigDecimal("1.20")) : null)
                .pujaMinima(minBid)
                .pujaMaxima(base != null ? base.multiply(new BigDecimal("1.20")) : null)
                .secondsLeft(secondsLeft)
                .title("Nuevo lote")
                .message("La subasta avanzo al siguiente lote")
                .build();
        realtimeEventPublisher.publishAuctionEvent(subastaId, event);
    }

    private void publishAuctionFinished(Integer subastaId) {
        AuctionRealtimeEvent event = AuctionRealtimeEvent.builder()
                .type(RealtimeEventType.AUCTION_FINISHED)
                .subastaId(subastaId)
                .timestamp(LocalDateTime.now())
                .secondsLeft(0)
                .title("Subasta finalizada")
                .message("No quedan lotes disponibles")
                .build();
        realtimeEventPublisher.publishAuctionEvent(subastaId, event);
    }

    private int secondsLeft(AuctionTimerState state) {
        long millis = Math.max(0, Duration.between(Instant.now(), state.deadline()).toMillis());
        return (int) ((millis + 999) / 1000);
    }

    private record AuctionTimerState(Integer itemId, Instant deadline) {
    }

    private record TimerInitialization(Integer itemId, boolean auctionFinished) {
    }

    private record LotClosureResult(Integer closedItemId, ItemCatalogo nextItem) {
    }
}
