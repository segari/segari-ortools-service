package id.segari.ortools.external;

import id.segari.ortools.error.SegariRoutingErrors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OSRMRestServiceImpl implements OSRMRestService {

    private static final String ANNOTATIONS = "duration,distance";
    private static final String OSRM_OK = "Ok";
    private final RestClient restClient;

    public OSRMRestServiceImpl(@Qualifier("osrmRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public OSRMTableResponseDTO getMatrix(List<LatLong> locations) {
        return fetchMatrix(locations, null);
    }

    @Override
    public OSRMTableResponseDTO getMatrixWithScaleFactor(List<LatLong> locations, double scaleFactor) {
        return fetchMatrix(locations, scaleFactor);
    }

    private OSRMTableResponseDTO fetchMatrix(List<LatLong> locations, Double scaleFactor) {
        String coordinates = buildCoordinates(locations);
        OSRMApiResponseDTO response = callOsrmApi(coordinates, scaleFactor);
        validateResponse(response);
        return convertResponse(response);
    }

    private String buildCoordinates(List<LatLong> locations) {
        return locations.stream()
                .map(loc -> loc.longitude() + "," + loc.latitude())
                .collect(Collectors.joining(";"));
    }

    private OSRMApiResponseDTO callOsrmApi(String coordinates, Double scaleFactor) {
        if (scaleFactor == null) {
            return restClient.get()
                    .uri("/table/v1/driving/{coordinates}?annotations={annotations}",
                            coordinates, ANNOTATIONS)
                    .retrieve()
                    .body(OSRMApiResponseDTO.class);
        }
        return restClient.get()
                .uri("/table/v1/driving/{coordinates}?annotations={annotations}&scale_factor={scaleFactor}",
                        coordinates, ANNOTATIONS, scaleFactor)
                .retrieve()
                .body(OSRMApiResponseDTO.class);
    }

    private void validateResponse(OSRMApiResponseDTO response) {
        if (response == null) {
            throw SegariRoutingErrors.osrmNullResponse();
        }
        if (!OSRM_OK.equals(response.code())) {
            throw SegariRoutingErrors.osrmInvalidResponse(response.code());
        }
    }

    private OSRMTableResponseDTO convertResponse(OSRMApiResponseDTO response) {
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
