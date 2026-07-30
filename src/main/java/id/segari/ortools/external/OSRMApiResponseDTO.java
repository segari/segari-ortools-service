package id.segari.ortools.external;

import java.util.List;

/**
 * Raw OSRM table response.
 * <p>
 * The matrices are boxed on purpose. OSRM emits {@code null} for pairs it cannot route, and with a
 * primitive {@code double[][]} Jackson silently coerces those nulls to {@code 0.0} - making an
 * unroutable pair indistinguishable from a zero-metre one. {@code Double[][]} keeps that
 * distinction alive until {@code OSRMRestServiceImpl} maps it to {@link OSRMRestService#UNREACHABLE}.
 */
public record OSRMApiResponseDTO(
        String code,
        Double[][] durations,
        Double[][] distances,
        List<OSRMLocationDTO> destinations,
        List<OSRMLocationDTO> sources
) {
}
