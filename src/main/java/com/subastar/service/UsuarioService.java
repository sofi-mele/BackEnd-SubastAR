package com.subastar.service;

import com.subastar.dto.usuario.*;
import com.subastar.exception.ResourceNotFoundException;
import com.subastar.model.*;
import com.subastar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UsuarioService {

    private final CredencialRepository credencialRepository;
    private final ClienteRepository clienteRepository;
    private final PaisRepository paisRepository;
    private final PersonaRepository personaRepository;
    private final PujoRepository pujoRepository;
    private final RegistroDeSubastaRepository registroDeSubastaRepository;
    private final MultaRepository multaRepository;
    private final PujoExtraRepository pujoExtraRepository;

    public UsuarioDetalle getMe(String email) {
        Credencial cred = credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Cliente cliente = clienteRepository.findById(cred.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        return buildDetalle(cliente, email);
    }

    @Transactional
    public UsuarioDetalle actualizarMe(String email, ActualizarUsuarioRequest req) {
        Credencial cred = credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Cliente cliente = clienteRepository.findById(cred.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        Persona persona = cliente.getPersona();

        if (req.getDomicilio() != null) {
            persona.setDireccion(req.getDomicilio());
            personaRepository.save(persona);
        }
        if (req.getPaisOrigen() != null) {
            Pais pais = paisRepository.findAll().stream()
                    .filter(p -> p.getNombre() != null && p.getNombre().equalsIgnoreCase(req.getPaisOrigen())
                            || p.getNombreCorto() != null && p.getNombreCorto().equalsIgnoreCase(req.getPaisOrigen()))
                    .findFirst().orElse(null);
            if (pais != null) cliente.setPais(pais);
        }
        clienteRepository.save(cliente);
        return buildDetalle(cliente, email);
    }

    public EstadoCuentaResponse getEstadoCuenta(String email) {
        Credencial cred = credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Cliente cliente = clienteRepository.findById(cred.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));

        BigDecimal multaPendiente = multaRepository.sumMultasPendientesByClienteId(cliente.getIdentificador());
        String estado;
        String mensaje = null;

        if (!"si".equals(cliente.getAdmitido())) {
            estado = "bloqueado";
            mensaje = "Cuenta bloqueada. Contacte al soporte.";
        } else if (multaPendiente != null && multaPendiente.compareTo(BigDecimal.ZERO) > 0) {
            estado = "multado";
            mensaje = "Tenés una multa pendiente de pago de $" + multaPendiente;
        } else {
            estado = "activo";
            mensaje = "Tu cuenta está activa y al día.";
        }
        return new EstadoCuentaResponse(estado, multaPendiente, mensaje);
    }

    public List<MultaResponse> getMultas(String email) {
        Credencial cred = credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Integer clienteId = cred.getPersonaId();

        List<Multa> multas = multaRepository
                .findByClienteIdentificadorOrderByCreadoEnDesc(clienteId);
        List<Integer> registroIds = multas.stream()
                .map(Multa::getRegistroId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, RegistroDeSubasta> registros = registroDeSubastaRepository.findAllById(registroIds).stream()
                .collect(Collectors.toMap(RegistroDeSubasta::getIdentificador, registro -> registro));

        return multas.stream().map(multa -> {
            RegistroDeSubasta registro = registros.get(multa.getRegistroId());
            String descripcion = registro != null && registro.getProducto() != null
                    ? registro.getProducto().getDescripcionCatalogo()
                    : null;
            return MultaResponse.builder()
                    .id(multa.getId())
                    .monto(multa.getMonto())
                    .estado(multa.getEstado())
                    .fecha(multa.getCreadoEn())
                    .motivo("No cuenta con el monto suficiente para cubrir el pago. Debe abonarlo antes de las 72hs, de lo contrario el caso será derivado a la justicia.")
                    .registroId(multa.getRegistroId())
                    .compraId(multa.getRegistroId())
                    .descripcionCompra(descripcion)
                    .build();
        }).collect(Collectors.toList());
    }

    public MetricasResponse getMetricas(String email) {
        Credencial cred = credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Integer clienteId = cred.getPersonaId();

        List<Pujo> todosPujos = pujoRepository.findByClienteId(clienteId);
        List<Pujo> ganados = pujoRepository.findGanadoresByClienteId(clienteId);
        List<RegistroDeSubasta> compras = registroDeSubastaRepository.findByClienteIdentificador(clienteId);

        long subastasParticipadas = todosPujos.stream()
                .map(p -> p.getItem().getCatalogo().getSubasta().getIdentificador())
                .distinct().count();
        long subastasGanadas = ganados.stream()
                .map(p -> p.getItem().getCatalogo().getSubasta().getIdentificador())
                .distinct().count();

        double tasaExito = subastasParticipadas > 0
                ? (double) subastasGanadas / subastasParticipadas : 0;

        BigDecimal totalOfertado = todosPujos.stream()
                .map(Pujo::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPagado = compras.stream()
                .map(RegistroDeSubasta::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ofertaPromedio = todosPujos.isEmpty() ? BigDecimal.ZERO
                : totalOfertado.divide(BigDecimal.valueOf(todosPujos.size()), 2, RoundingMode.HALF_UP);
        BigDecimal ofertaMasAlta = todosPujos.stream()
                .map(Pujo::getImporte).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal ofertaMasBaja = todosPujos.stream()
                .map(Pujo::getImporte).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        // Ganadas por mes
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Long> porMes = ganados.stream()
                .flatMap(p -> {
                    return pujoExtraRepository.findByPujoId(p.getIdentificador())
                            .map(pe -> Map.entry(pe.getTimestampPuja().format(fmt), 1L))
                            .stream();
                })
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.counting()));

        List<MetricasResponse.GanadasPorMes> ganadasPorMes = porMes.entrySet().stream()
                .map(e -> {
                    MetricasResponse.GanadasPorMes g = new MetricasResponse.GanadasPorMes();
                    g.setMes(e.getKey());
                    g.setCantidad(e.getValue().intValue());
                    return g;
                }).collect(Collectors.toList());

        MetricasResponse resp = new MetricasResponse();
        resp.setSubastasParticipadas((int) subastasParticipadas);
        resp.setSubastasGanadas((int) subastasGanadas);
        resp.setTasaExito(tasaExito);
        resp.setTotalOfertado(totalOfertado);
        resp.setTotalPagado(totalPagado);
        resp.setOfertaPromedio(ofertaPromedio);
        resp.setOfertaMasAlta(ofertaMasAlta);
        resp.setOfertaMasBaja(ofertaMasBaja);
        resp.setGanadasPorMes(ganadasPorMes);
        return resp;
    }

    public List<ParticipacionPerdidaResponse> getParticipacionesPerdidas(String email) {
        Credencial cred = credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Integer clienteId = cred.getPersonaId();

        List<Pujo> pujas = pujoRepository.findParticipacionesPerdidas(clienteId);

        Map<Integer, List<Pujo>> porItem = pujas.stream()
                .collect(Collectors.groupingBy(p -> p.getItem().getIdentificador()));

        List<ParticipacionPerdidaResponse> result = new ArrayList<>();

        for (Map.Entry<Integer, List<Pujo>> entry : porItem.entrySet()) {
            Integer itemId = entry.getKey();
            List<Pujo> pujasItem = entry.getValue();

            Pujo mejorPuja = pujasItem.stream()
                    .max(Comparator.comparing(Pujo::getImporte))
                    .orElse(null);
            if (mejorPuja == null) continue;

            LocalDateTime fechaPuja = pujoExtraRepository.findByPujoId(mejorPuja.getIdentificador())
                    .map(PujoExtra::getTimestampPuja)
                    .orElse(null);

            Pujo ganador = pujoRepository.findGanadorByItemId(itemId).orElse(null);

            ItemCatalogo item = mejorPuja.getItem();
            Producto producto = item.getProducto();

            BigDecimal precioFinalVenta = null;
            String nombreGanador = null;
            if (ganador != null) {
                precioFinalVenta = ganador.getImporte();
                Persona persona = ganador.getAsistente().getCliente().getPersona();
                if (persona != null) nombreGanador = persona.getNombre();
            }

            Integer subastaId = item.getCatalogo() != null && item.getCatalogo().getSubasta() != null
                    ? item.getCatalogo().getSubasta().getIdentificador() : null;

            result.add(ParticipacionPerdidaResponse.builder()
                    .itemId(itemId)
                    .nombreProducto(producto != null ? producto.getDescripcionCatalogo() : null)
                    .descripcion(producto != null ? producto.getDescripcionCompleta() : null)
                    .precioBase(item.getPrecioBase())
                    .miMejorPuja(mejorPuja.getImporte())
                    .precioFinalVenta(precioFinalVenta)
                    .nombreGanador(nombreGanador)
                    .fechaPuja(fechaPuja)
                    .subastaId(subastaId)
                    .build());
        }

        result.sort(Comparator.comparing(ParticipacionPerdidaResponse::getFechaPuja,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }

    private UsuarioDetalle buildDetalle(Cliente cliente, String email) {
        UsuarioDetalle d = new UsuarioDetalle();
        d.setId(cliente.getIdentificador());
        Persona p = cliente.getPersona();
        if (p != null) {
            String[] parts = p.getNombre() != null ? p.getNombre().split(" ", 2) : new String[]{"", ""};
            d.setNombre(parts[0]);
            d.setApellido(parts.length > 1 ? parts[1] : "");
            d.setDomicilio(p.getDireccion());
            d.setDni(p.getDocumento());
        }
        d.setEmail(email);
        d.setCategoria(cliente.getCategoria() != null ? cliente.getCategoria() : "comun");
        d.setEstado("si".equals(cliente.getAdmitido()) ? "activo" : "bloqueado");
        if (cliente.getPais() != null) d.setPaisOrigen(cliente.getPais().getNombre());
        return d;
    }
}
