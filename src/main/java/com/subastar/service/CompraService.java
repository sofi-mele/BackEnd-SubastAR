package com.subastar.service;

import com.subastar.dto.compra.CompraDetalle;
import com.subastar.dto.compra.CompraResumen;
import com.subastar.dto.compra.RegularizarPagoRequest;
import com.subastar.exception.ForbiddenException;
import com.subastar.exception.ResourceNotFoundException;
import com.subastar.model.*;
import com.subastar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CompraService {

    private final MedioPagoService medioPagoService;
    private final RegistroDeSubastaRepository registroRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final MultaRepository multaRepository;
    private final MedioPagoRepository medioPagoRepository;
    private final CredencialRepository credencialRepository;
    private final NotificacionService notificacionService;
    private final SeguroRepository seguroRepository;
    private final SeguroExtraRepository seguroExtraRepository;

    public List<CompraResumen> listar(String email, String estadoPago, String estadoEntrega) {
        Integer clienteId = getClienteId(email);
        return registroRepository.findByClienteIdentificador(clienteId).stream()
                .filter(r -> {
                    CompraExtra extra = compraExtraRepository.findByRegistroId(r.getIdentificador()).orElse(null);
                    if (estadoPago != null && extra != null && !estadoPago.equals(extra.getEstadoPago())) return false;
                    if (estadoEntrega != null && extra != null && !estadoEntrega.equals(extra.getEstadoEntrega())) return false;
                    return true;
                })
                .map(r -> toResumen(r, compraExtraRepository.findByRegistroId(r.getIdentificador()).orElse(null)))
                .collect(Collectors.toList());
    }

    public CompraDetalle getDetalle(String email, Integer compraId) {
        Integer clienteId = getClienteId(email);
        RegistroDeSubasta r = registroRepository.findById(compraId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));
        if (!r.getCliente().getIdentificador().equals(clienteId)) {
            throw new ForbiddenException("La compra no pertenece al usuario");
        }
        CompraExtra extra = compraExtraRepository.findByRegistroId(compraId).orElse(null);
        return toDetalle(r, extra);
    }

    @Transactional
    public CompraDetalle regularizarPago(String email, Integer compraId, RegularizarPagoRequest req) {
        Integer clienteId = getClienteId(email);
        RegistroDeSubasta r = registroRepository.findById(compraId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));
        if (!r.getCliente().getIdentificador().equals(clienteId)) {
            throw new ForbiddenException("La compra no pertenece al usuario");
        }
        medioPagoRepository.findByIdAndClienteIdentificadorAndEliminadoFalse(req.getMedioPagoId(), clienteId)
                .orElseThrow(() -> new ForbiddenException("El medio de pago no pertenece al usuario"));

        CompraExtra extra = compraExtraRepository.findByRegistroId(compraId)
                .orElseGet(() -> {
                    CompraExtra ce = new CompraExtra();
                    ce.setRegistroId(compraId);
                    return ce;
                });
        extra.setMedioPagoId(req.getMedioPagoId());
        extra.setEstadoPago("pagado");
        compraExtraRepository.save(extra);

        List<Multa> multasPendientes = multaRepository.findByClienteIdentificadorAndEstado(clienteId, "pendiente");
        for (Multa multa : multasPendientes) {
            if (compraId.equals(multa.getRegistroId())) {
                multa.setEstado("cancelada");
                multaRepository.save(multa);
            }
        }

        medioPagoService.recalcularCategoria(clienteId);

        // M-10: notificar al cliente que su pago fue registrado
        CompraDetalle detalle = toDetalle(r, extra);
        String nombreItem = r.getProducto().getDescripcionCatalogo() != null
                ? r.getProducto().getDescripcionCatalogo() : "Compra #" + compraId;
        notificacionService.notificarPagoRegularizado(r.getCliente(), nombreItem, detalle.getTotal());
        return detalle;
    }

    private CompraResumen toResumen(RegistroDeSubasta r, CompraExtra extra) {
        asociarPolizaAlGanador(r);
        CompraResumen c = new CompraResumen();
        c.setId(r.getIdentificador());
        c.setNombreItem(r.getProducto().getDescripcionCatalogo());
        c.setSubasta("Subasta #" + r.getSubasta().getIdentificador());
        c.setValorPujado(r.getImporte());
        c.setComision(r.getComision());
        String nroPoliza = r.getProducto() != null ? r.getProducto().getSeguroNroPoliza() : null;
        c.setPolizaId(nroPoliza);
        c.setNumeroPoliza(nroPoliza);

        if (extra != null) {
            c.setFecha(extra.getFechaCompra());
            c.setEstadoPago(extra.getEstadoPago());
            c.setEstadoEntrega(extra.getEstadoEntrega());
        } else {
            c.setEstadoPago("pendiente");
            c.setEstadoEntrega("coordinando");
        }

        BigDecimal multaTotal = multaRepository
                .sumMultasPendientesByClienteId(r.getCliente().getIdentificador());
        c.setMulta(multaTotal.compareTo(BigDecimal.ZERO) > 0 ? multaTotal : null);
        return c;
    }

    private CompraDetalle toDetalle(RegistroDeSubasta r, CompraExtra extra) {
        CompraDetalle d = new CompraDetalle();
        CompraResumen base = toResumen(r, extra);
        d.setId(base.getId()); d.setNombreItem(base.getNombreItem());
        d.setSubasta(base.getSubasta()); d.setFecha(base.getFecha());
        d.setValorPujado(base.getValorPujado()); d.setComision(base.getComision()); d.setMulta(base.getMulta());
        d.setEstadoPago(base.getEstadoPago()); d.setEstadoEntrega(base.getEstadoEntrega());
        d.setPolizaId(base.getPolizaId()); d.setNumeroPoliza(base.getNumeroPoliza());

        if (extra != null) {
            if (extra.getMedioPagoId() != null) {
                medioPagoRepository.findById(extra.getMedioPagoId())
                        .ifPresent(mp -> d.setMedioPago(mp.getDescripcion()));
            }
            d.setCostoEnvio(extra.getCostoEnvio());
            d.setDireccionEntrega(extra.getDireccionEntrega());
            if (extra.getFacturaPath() != null) {
                d.setFacturaUrl("/api/v1/compras/" + r.getIdentificador() + "/factura");
            }
        }

        BigDecimal total = r.getImporte();
        if (extra != null && extra.getCostoEnvio() != null) total = total.add(extra.getCostoEnvio());
        if (base.getMulta() != null) total = total.add(base.getMulta());
        d.setTotal(total);
        return d;
    }

    private void asociarPolizaAlGanador(RegistroDeSubasta registro) {
        if (registro.getProducto() == null) return;
        String nroPoliza = registro.getProducto().getSeguroNroPoliza();
        if (nroPoliza == null || nroPoliza.isBlank()) return;
        if (!seguroRepository.existsById(nroPoliza)) {
            return;
        }

        Integer clienteGanadorId = registro.getCliente().getIdentificador();
        SeguroExtra extra = seguroExtraRepository.findById(nroPoliza).orElseGet(() -> {
            SeguroExtra nuevo = new SeguroExtra();
            nuevo.setPolizaId(nroPoliza);
            nuevo.setVigenciaDesde(LocalDate.now());
            nuevo.setVigenciaHasta(LocalDate.now().plusYears(1));
            nuevo.setCobertura("Cobertura asociada a compra ganada en subasta");
            return nuevo;
        });

        if (extra.getBeneficiarioId() != null && !extra.getBeneficiarioId().equals(clienteGanadorId)) {
            return;
        }
        if (extra.getBeneficiarioId() == null) {
            extra.setBeneficiarioId(clienteGanadorId);
            seguroExtraRepository.save(extra);
            medioPagoService.recalcularCategoria(registro.getCliente().getIdentificador());
        }
    }

    public void registrarPreferenciaEntrega(String email, Integer compraId, com.subastar.dto.compra.PreferenciaEntregaRequest req) {
        Integer clienteId = getClienteId(email);
        RegistroDeSubasta registro = registroRepository.findById(compraId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));
        if (!registro.getCliente().getIdentificador().equals(clienteId)) {
            throw new com.subastar.exception.ForbiddenException("La compra no pertenece al usuario");
        }

        CompraExtra extra = compraExtraRepository.findByRegistroId(compraId)
                .orElseThrow(() -> new ResourceNotFoundException("Detalle de compra no encontrado"));

        String tipo = req.getTipo();
        if (!"retiro".equals(tipo) && !"envio".equals(tipo)) {
            throw new com.subastar.exception.BadRequestException("El tipo debe ser 'retiro' o 'envio'");
        }
        if ("envio".equals(tipo) && (req.getDireccion() == null || req.getDireccion().isBlank())) {
            throw new com.subastar.exception.BadRequestException("Debés indicar la dirección de entrega");
        }

        extra.setEstadoEntrega(tipo);
        if ("envio".equals(tipo)) {
            extra.setDireccionEntrega(req.getDireccion());
        }
        compraExtraRepository.save(extra);

        Cliente cliente = registro.getCliente();
        String nombreItem = registro.getProducto().getDescripcionCatalogo() != null
                ? registro.getProducto().getDescripcionCatalogo()
                : "Producto #" + registro.getProducto().getIdentificador();

        if ("retiro".equals(tipo)) {
            notificacionService.notificarRetiroPersonal(cliente, nombreItem);
        } else {
            notificacionService.notificarConfirmacionEnvio(cliente, nombreItem, req.getDireccion());
        }
    }

    private Integer getClienteId(String email) {
        return credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"))
                .getPersonaId();
    }
}
