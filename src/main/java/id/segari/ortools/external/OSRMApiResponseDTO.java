package id.segari.ortools.external;

import java.util.List;

public record OSRMApiResponseDTO(
        String code,
        double[][] durations,
        double[][] distances,
        List<OSRMLocationDTO> destinations,
        List<OSRMLocationDTO> sources
) {
}
