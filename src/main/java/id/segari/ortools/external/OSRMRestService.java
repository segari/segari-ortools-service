package id.segari.ortools.external;

import java.util.List;

public interface OSRMRestService {

    /**
     * Get distance matrix from OSRM table service
     * @param locations List of latitude/longitude coordinates
     * @return Distance and duration matrices
     */
    OSRMTableResponseDTO getDistanceMatrix(List<LatLong> locations);
}
