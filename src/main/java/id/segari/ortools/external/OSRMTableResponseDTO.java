package id.segari.ortools.external;

public record OSRMTableResponseDTO(
        long[][] durations,
        long[][] distances
) {
}
