package com.subastar.service;

import com.subastar.dto.realtime.AuctionRealtimeEvent;
import com.subastar.dto.realtime.RealtimeEventType;
import com.subastar.model.*;
import com.subastar.realtime.RealtimeEventPublisher;
import com.subastar.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CierreLoteService {

    private final SubastaExtraRepository subastaExtraRepository;
    private final SubastaRepository subastaRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final PujoRepository pujoRepository;
    private final PujoExtraRepository pujoExtraRepository;
    private final RegistroDeSubastaRepository registroDeSubastaRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final ClienteRepository clienteRepository;
    private final NotificacionService notificacionService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Transactional
    public void cerrarLote(Integer subastaId) {
        SubastaExtra extra = subastaExtraRepository.findBySubastaId(subastaId).orElse(null);
        if (extra == null || extra.getItemActualId() == null) return;

        ItemCatalogo item = itemCatalogoRepository.findById(extra.getItemActualId()).orElse(null);
        if (item == null) return;

        Subasta subasta = subastaRepository.findById(subastaId).orElse(null);
        if (subasta == null) return;

        Pujo ganador = pujoRepository
                .findTopByItemIdentificadorOrderByImporteDesc(item.getIdentificador())
                .orElse(null);

        if (ganador != null) {
            ganador.setGanador("si");
            pujoRepository.save(ganador);

            RegistroDeSubasta registro = new RegistroDeSubasta();
            registro.setSubasta(subasta);
            registro.setDuenio(item.getProducto().getDuenio());
            registro.setProducto(item.getProducto());
            registro.setCliente(ganador.getAsistente().getCliente());
            registro.setImporte(ganador.getImporte());
            registro.setComision(item.getComision());
            registro = registroDeSubastaRepository.save(registro);

            CompraExtra compraExtra = new CompraExtra();
            compraExtra.setRegistroId(registro.getIdentificador());
            PujoExtra pujoExtra = pujoExtraRepository.findByPujoId(ganador.getIdentificador()).orElse(null);
            if (pujoExtra != null) compraExtra.setMedioPagoId(pujoExtra.getMedioPagoId());
            compraExtraRepository.save(compraExtra);

            item.setSubastado("si");
            itemCatalogoRepository.save(item);

            String nombreItem = item.getProducto().getDescripcionCatalogo() != null
                    ? item.getProducto().getDescripcionCatalogo() : "Ítem #" + item.getIdentificador();
            notificacionService.notificarGanadorSubasta(
                    ganador.getAsistente().getCliente(), nombreItem,
                    ganador.getImporte(), item.getComision());

            log.info("Lote cerrado: subasta={} item={} ganador=cliente:{} importe={}",
                    subastaId, item.getIdentificador(),
                    ganador.getAsistente().getCliente().getIdentificador(), ganador.getImporte());
        } else {
            Cliente empresa = clienteRepository.findById(1005).orElse(null);
            if (empresa != null) {
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
                compraExtraRepository.save(compraExtra);
            } else {
                log.warn("Auto-compra empresa: cliente id=1005 no encontrado, no se creó registro para item={}",
                        item.getIdentificador());
            }

            item.setSubastado("si");
            itemCatalogoRepository.save(item);
            log.info("Auto-compra empresa: subasta={} item={} precio_base={}",
                    subastaId, item.getIdentificador(), item.getPrecioBase());
        }

        List<ItemCatalogo> siguientes = itemCatalogoRepository
                .findNextAvailableItems(subastaId, item.getIdentificador());

        if (!siguientes.isEmpty()) {
            ItemCatalogo siguiente = siguientes.get(0);
            extra.setItemActualId(siguiente.getIdentificador());
            extra.setFechaInicioLote(LocalDateTime.now());
            extra.setFechaUltimaPuja(null);
            subastaExtraRepository.save(extra);

            BigDecimal precioBase = siguiente.getPrecioBase();
            AuctionRealtimeEvent event = AuctionRealtimeEvent.builder()
                    .type(RealtimeEventType.LOT_CHANGED)
                    .subastaId(subastaId)
                    .itemId(siguiente.getIdentificador())
                    .timestamp(LocalDateTime.now())
                    .pujaMinima(precioBase)
                    .pujaMaxima(precioBase != null ? precioBase.multiply(new BigDecimal("1.20")) : null)
                    .build();
            realtimeEventPublisher.publishAuctionEvent(subastaId, event);
            log.info("Lote avanzado: subasta={} siguiente_item={}", subastaId, siguiente.getIdentificador());
        } else {
            subasta.setEstado("cerrada");
            subastaRepository.save(subasta);
            extra.setItemActualId(null);
            extra.setFechaInicioLote(null);
            extra.setFechaUltimaPuja(null);
            subastaExtraRepository.save(extra);

            AuctionRealtimeEvent event = AuctionRealtimeEvent.builder()
                    .type(RealtimeEventType.AUCTION_FINISHED)
                    .subastaId(subastaId)
                    .timestamp(LocalDateTime.now())
                    .build();
            realtimeEventPublisher.publishAuctionEvent(subastaId, event);
            log.info("Subasta {} cerrada automáticamente (sin más ítems)", subastaId);
        }
    }
}
