package id.segari.ortools.external;

public record OSRMLocationDTO(
        double[] location,
        String name,
        double distance,
        String hint
) {
}
