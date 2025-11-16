package id.segari.ortools.external;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OSRMRestServiceImpl implements OSRMRestService {

    private static final String ANNOTATIONS = "duration,distance";
    private final RestClient restClient;

    public OSRMRestServiceImpl(@Qualifier("osrmRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public OSRMTableResponseDTO getDistanceMatrix(List<LatLong> locations) {
        // Build coordinates string: lon1,lat1;lon2,lat2;...
        String coordinates = locations.stream()
                .map(loc -> loc.longitude() + "," + loc.latitude())
                .collect(Collectors.joining(";"));

        // Call OSRM API
        OSRMApiResponseDTO response = restClient.get()
                .uri("/table/v1/driving/{coordinates}?annotations={annotations}",
                        coordinates, ANNOTATIONS)
                .retrieve()
                .body(OSRMApiResponseDTO.class);

        if (response == null) {
            throw new RuntimeException("OSRM API returned null response");
        }

        // Convert double[][] to long[][]
        long[][] durations = convertToLongMatrix(response.durations());
        long[][] distances = convertToLongMatrix(response.distances());

        return new OSRMTableResponseDTO(durations, distances);
    }

    private long[][] convertToLongMatrix(double[][] matrix) {
        if (matrix == null) {
            return new long[0][0];
        }

        long[][] result = new long[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            result[i] = new long[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                result[i][j] = Math.round(matrix[i][j]);
            }
        }
        return result;
    }
}
