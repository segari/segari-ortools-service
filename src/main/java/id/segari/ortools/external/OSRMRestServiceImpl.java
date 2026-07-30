package id.segari.ortools.external;

import id.segari.ortools.error.SegariRoutingErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OSRMRestServiceImpl implements OSRMRestService {

    private static final Logger log = LoggerFactory.getLogger(OSRMRestServiceImpl.class);

    private static final String ANNOTATIONS = "duration,distance";
    private static final String OSRM_OK = "Ok";
    private static final int MAX_LOGGED_PAIRS = 10;
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
        return convertResponse(response, locations);
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

    private OSRMTableResponseDTO convertResponse(OSRMApiResponseDTO response, List<LatLong> locations) {
        long[][] durations = convertToLongMatrix(response.durations(), "durations", locations);
        long[][] distances = convertToLongMatrix(response.distances(), "distances", locations);
        return new OSRMTableResponseDTO(durations, distances);
    }

    /**
     * Converts an OSRM matrix to longs, mapping every unroutable cell to {@link #UNREACHABLE}.
     * OSRM nulls an individual cell when it cannot route that pair, and a whole row when the source
     * itself is unroutable.
     */
    private long[][] convertToLongMatrix(Double[][] matrix, String annotation, List<LatLong> locations) {
        if (matrix == null) {
            return new long[0][0];
        }
        long[][] result = new long[matrix.length][];
        List<String> unroutablePairs = new ArrayList<>();
        int unroutableCount = 0;

        for (int i = 0; i < matrix.length; i++) {
            Double[] row = matrix[i];
            if (row == null) {
                result[i] = new long[matrix.length];
                Arrays.fill(result[i], UNREACHABLE);
                unroutableCount += matrix.length;
                addPair(unroutablePairs, locations, i, -1);
                continue;
            }
            result[i] = new long[row.length];
            for (int j = 0; j < row.length; j++) {
                if (row[j] == null) {
                    result[i][j] = UNREACHABLE;
                    unroutableCount++;
                    addPair(unroutablePairs, locations, i, j);
                } else {
                    result[i][j] = Math.round((double) row[j]);
                }
            }
        }

        if (unroutableCount > 0) {
            log.warn("OSRM could not route {} {} cell(s) of {}x{}; mapped to UNREACHABLE={}. First pairs: {}",
                    unroutableCount, annotation, matrix.length, matrix.length, UNREACHABLE, unroutablePairs);
        }
        return result;
    }

    private void addPair(List<String> pairs, List<LatLong> locations, int from, int to) {
        if (pairs.size() >= MAX_LOGGED_PAIRS) return;
        pairs.add(describe(locations, from) + " -> " + (to < 0 ? "*" : describe(locations, to)));
    }

    private String describe(List<LatLong> locations, int index) {
        if (locations == null || index >= locations.size()) return "#" + index;
        LatLong location = locations.get(index);
        return "#" + index + "(" + location.latitude() + "," + location.longitude() + ")";
    }
}
