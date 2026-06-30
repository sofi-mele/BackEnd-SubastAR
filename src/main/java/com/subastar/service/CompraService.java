package com.subastar.service;

import com.subastar.dto.compra.CompraDetalle;
import com.subastar.dto.compra.CompraResumen;
import com.subastar.dto.compra.RegularizarPagoRequest;
import com.subastar.exception.BadRequestException;
import com.subastar.exception.ForbiddenException;
import com.subastar.exception.ResourceNotFoundException;
import com.subastar.model.*;
import com.subastar.repository.*;
import com.subastar.model.Cliente;
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
    private final ChequeCertificadoRepository chequeCertificadoRepository;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final TarjetaCreditoRepository tarjetaCreditoRepository;
    private final SubastaExtraRepository subastaExtraRepository;
    private final CredencialRepository credencialRepository;
    private final ClienteRepository clienteRepository;
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
        MedioPago medioPago = medioPagoRepository.findByIdAndClienteIdentificadorAndEliminadoFalse(req.getMedioPagoId(), clienteId)
                .orElseThrow(() -> new ForbiddenException("El medio de pago no pertenece al usuario"));

        if ("cheque_certificado".equals(medioPago.getTipo())) {
            ChequeCertificado cheque = chequeCertificadoRepository.findByMedioPagoId(medioPago.getId())
                    .orElseThrow(() -> new BadRequestException("Cheque certificado no encontrado"));
            BigDecimal yaUsado = compraExtraRepository.sumImportePagadoConMedioPago(medioPago.getId(), compraId);
            BigDecimal disponible = cheque.getMontoCertificado().subtract(yaUsado);
            if (r.getImporte().compareTo(disponible) > 0) {
                if (!multaRepository.existsByRegistroId(compraId)) {
                    BigDecimal montoMulta = r.getImporte().multiply(new BigDecimal("0.10"));
                    Multa multa = new Multa();
                    multa.setCliente(r.getCliente());
                    multa.setRegistroId(compraId);
                    multa.setMonto(montoMulta);
                    multa.setEstado("pendiente");
                    multaRepository.save(multa);
                    r.getCliente().setAdmitido("no");
                    clienteRepository.save(r.getCliente());
                    notificacionService.notificarMulta(r.getCliente(), montoMulta, "Fondos insuficientes para cubrir el pago");
                }
                throw new BadRequestException(
                        "El saldo disponible del cheque ($" + disponible + ") no alcanza para cubrir esta compra ($" + r.getImporte() + ")");
            }
        }

        subastaExtraRepository.findBySubastaId(r.getSubasta().getIdentificador()).ifPresent(subastaExtra -> {
            if ("USD".equals(subastaExtra.getMoneda())) {
                if ("cheque_certificado".equals(medioPago.getTipo())) {
                    throw new ForbiddenException("Los cheques certificados no son válidos para subastas en dólares.");
                }
                if ("cuenta_bancaria".equals(medioPago.getTipo())) {
                    cuentaBancariaRepository.findByMedioPagoId(medioPago.getId()).ifPresent(cb -> {
                        if ("Argentina".equalsIgnoreCase(cb.getPaisBanco())) {
                            throw new ForbiddenException("Para subastas en dólares necesitás una cuenta bancaria de un banco extranjero.");
                        }
                    });
                }
            }
        });

        CompraExtra extra = compraExtraRepository.findByRegistroId(compraId)
                .orElseGet(() -> {
                    CompraExtra ce = new CompraExtra();
                    ce.setRegistroId(compraId);
                    return ce;
                });
        extra.setMedioPagoId(req.getMedioPagoId());
        extra.setEstadoPago("pagado");
        compraExtraRepository.save(extra);

        // Capturar multa de esta compra ANTES de cancelarla para usarla en el total
        BigDecimal multaDeEstaCompra = multaRepository.findByClienteIdentificadorAndEstado(clienteId, "pendiente")
                .stream()
                .filter(m -> compraId.equals(m.getRegistroId()))
                .map(Multa::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Multa> multasPendientes = multaRepository.findByClienteIdentificadorAndEstado(clienteId, "pendiente");
        for (Multa multa : multasPendientes) {
            if (compraId.equals(multa.getRegistroId())) {
                multa.setEstado("cancelada");
                multaRepository.save(multa);
            }
        }

        boolean sinMultasRestantes = multaRepository
                .findByClienteIdentificadorAndEstado(clienteId, "pendiente").isEmpty();
        if (sinMultasRestantes) {
            clienteRepository.findById(clienteId).ifPresent(c -> {
                c.setAdmitido("si");
                clienteRepository.save(c);
            });
        }

        medioPagoService.recalcularCategoria(clienteId);

        // M-10: notificar al cliente que su pago fue registrado
        CompraDetalle detalle = toDetalle(r, extra);
        String nombreItem = r.getProducto().getDescripcionCatalogo() != null
                ? r.getProducto().getDescripcionCatalogo() : "Compra #" + compraId;
        BigDecimal totalNotificacion = r.getImporte();
        if (r.getComision() != null) totalNotificacion = totalNotificacion.add(r.getComision());
        if (multaDeEstaCompra.compareTo(BigDecimal.ZERO) > 0) totalNotificacion = totalNotificacion.add(multaDeEstaCompra);
        notificacionService.notificarPagoRegularizado(r.getCliente(), nombreItem, totalNotificacion);
        return detalle;
    }

    @Transactional
    public void declararInsolvencia(String email, Integer compraId) {
        Integer clienteId = getClienteId(email);
        RegistroDeSubasta r = registroRepository.findById(compraId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));
        if (!r.getCliente().getIdentificador().equals(clienteId)) {
            throw new ForbiddenException("La compra no pertenece al usuario");
        }

        CompraExtra extra = compraExtraRepository.findByRegistroId(compraId).orElse(null);
        if (extra != null && "pagado".equals(extra.getEstadoPago())) {
            throw new BadRequestException("Esta compra ya fue pagada");
        }

        if (multaRepository.existsByRegistroId(compraId)) {
            throw new BadRequestException("Ya existe una multa registrada para esta compra");
        }

        BigDecimal montoMulta = r.getImporte().multiply(new BigDecimal("0.10"));
        Multa multa = new Multa();
        multa.setCliente(r.getCliente());
        multa.setRegistroId(compraId);
        multa.setMonto(montoMulta);
        multa.setEstado("pendiente");
        multaRepository.save(multa);

        r.getCliente().setAdmitido("no");
        clienteRepository.save(r.getCliente());

        notificacionService.notificarMulta(r.getCliente(), montoMulta, "El usuario declaró no poseer fondos suficientes para cubrir el pago");
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
        subastaExtraRepository.findBySubastaId(r.getSubasta().getIdentificador())
                .ifPresent(se -> c.setMoneda(se.getMoneda()));
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
        if (r.getComision() != null) total = total.add(r.getComision());
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
