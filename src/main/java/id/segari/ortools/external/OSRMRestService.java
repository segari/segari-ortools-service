package id.segari.ortools.external;

import java.util.List;

public interface OSRMRestService {

    /**
     * Value substituted for a matrix cell that OSRM returned as null, meaning it could not route
     * that pair (disconnected component after snapping, one-way/restricted segment, ...).
     * <p>
     * Large enough to be rejected by every routing dimension, small enough that adding overhead and
     * slack on top of it cannot overflow a long. Never map an unroutable cell to 0: it would become
     * the cheapest possible arc and actively attract the solver.
     */
    long UNREACHABLE = Integer.MAX_VALUE / 4L;

    /**
     * Get distance matrix from OSRM table service
     * @param locations List of latitude/longitude coordinates
     * @return Distance and duration matrices, with unroutable cells set to {@link #UNREACHABLE}
     */
    OSRMTableResponseDTO getMatrix(List<LatLong> locations);
    OSRMTableResponseDTO getMatrixWithScaleFactor(List<LatLong> locations, double scaleFactor);
}
