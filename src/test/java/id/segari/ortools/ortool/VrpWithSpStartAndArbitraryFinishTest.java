package id.segari.ortools.ortool;

import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO.SegariRouteOrderEnum;
import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;
import id.segari.ortools.dto.route.v4.RouteV4DTO;
import id.segari.ortools.dto.route.v4.VrpResultDTO;
import id.segari.ortools.external.LatLong;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.external.OSRMTableResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the incident where two orders 10.5 km apart were batched onto one vehicle
 * despite a 5 km order-to-order cap, because OSRM returned null for that pair and the null was
 * silently deserialized as a distance of 0.
 */
class VrpWithSpStartAndArbitraryFinishTest {

    private static final long ORDER_A = 16043557L;
    private static final long ORDER_B = 16044090L;

    private static final int DUMMY = 0, SP = 1, A = 2, B = 3;

    /** Real osrm.segari.id output for the incident coordinates (durations already scaled x3). */
    private static long[][] realDistances() {
        return new long[][]{
                {0, 73588, 73721, 78611},
                {72015, 0, 5812, 8667},
                {71802, 5508, 0, 10470},
                {80184, 8718, 9833, 0}};
    }

    private static long[][] realDurations() {
        return new long[][]{
                {0, 10364, 10584, 11267},
                {10095, 0, 1273, 2029},
                {10433, 1230, 0, 2128},
                {11040, 1931, 2169, 0}};
    }

    private static RouteOrderV2DTO order(long id, SegariRouteOrderEnum type, double lat, double lng, long maxTimeWindow) {
        return new RouteOrderV2DTO(id, type, lat, lng, false, maxTimeWindow);
    }

    /** The exact order list from the incident request body. */
    private static List<RouteOrderV2DTO> incidentOrders() {
        return List.of(
                order(-2L, SegariRouteOrderEnum.DUMMY, 0.0, 0.0, 43200),
                order(-1L, SegariRouteOrderEnum.SP, -6.19742022712038, 106.898010979866, 1200),
                order(ORDER_A, SegariRouteOrderEnum.ORDER, -6.1726244, 106.9036136, 5029),
                order(ORDER_B, SegariRouteOrderEnum.ORDER, -6.23029067083391, 106.934339180589, 4033));
    }

    /** The exact parameters from the incident request body. */
    private static RouteV4DTO incidentRequest(List<RouteOrderV2DTO> orders) {
        return new RouteV4DTO(20000, 40000, 5000, 10000, 7, 4, 3.0, 420, 120, 2, 0, orders);
    }

    private static OSRMRestService stub(long[][] distances, long[][] durations) {
        return new OSRMRestService() {
            @Override
            public OSRMTableResponseDTO getMatrix(List<LatLong> locations) {
                return new OSRMTableResponseDTO(durations, distances);
            }

            @Override
            public OSRMTableResponseDTO getMatrixWithScaleFactor(List<LatLong> locations, double scaleFactor) {
                return new OSRMTableResponseDTO(durations, distances);
            }
        };
    }

    private static boolean sharesRoute(VrpResultDTO result, long first, long second) {
        return result.results().stream().anyMatch(route -> route.contains(first) && route.contains(second));
    }

    private static boolean contains(VrpResultDTO result, long orderId) {
        return result.results().stream().anyMatch(route -> route.contains(orderId));
    }

    @Test
    @DisplayName("baseline: 10.5 km apart exceeds the 5 km cap, so the pair is split")
    void realDistancesSplitThePair() {
        VrpResultDTO result = VrpWithSpStartAndArbitraryFinish.run(
                incidentRequest(incidentOrders()), stub(realDistances(), realDurations()));

        assertThat(contains(result, ORDER_A)).isTrue();
        assertThat(contains(result, ORDER_B)).isTrue();
        assertThat(sharesRoute(result, ORDER_A, ORDER_B)).isFalse();
    }

    @Test
    @DisplayName("a zero distance between distinct coordinates is rejected, not treated as a free arc")
    void zeroDistanceBetweenDistinctCoordinatesDoesNotBatch() {
        long[][] distances = realDistances();
        distances[A][B] = 0;
        distances[B][A] = 0;

        VrpResultDTO result = VrpWithSpStartAndArbitraryFinish.run(
                incidentRequest(incidentOrders()), stub(distances, realDurations()));

        assertThat(sharesRoute(result, ORDER_A, ORDER_B)).isFalse();
    }

    @Test
    @DisplayName("an UNREACHABLE distance is rejected")
    void unreachableDistanceDoesNotBatch() {
        long[][] distances = realDistances();
        distances[A][B] = OSRMRestService.UNREACHABLE;
        distances[B][A] = OSRMRestService.UNREACHABLE;

        VrpResultDTO result = VrpWithSpStartAndArbitraryFinish.run(
                incidentRequest(incidentOrders()), stub(distances, realDurations()));

        assertThat(sharesRoute(result, ORDER_A, ORDER_B)).isFalse();
    }

    @Test
    @DisplayName("two orders at the very same coordinate still batch on a genuine zero distance")
    void sameCoordinateOrdersStillBatch() {
        List<RouteOrderV2DTO> orders = List.of(
                order(-2L, SegariRouteOrderEnum.DUMMY, 0.0, 0.0, 43200),
                order(-1L, SegariRouteOrderEnum.SP, -6.19742022712038, 106.898010979866, 1200),
                order(ORDER_A, SegariRouteOrderEnum.ORDER, -6.1726244, 106.9036136, 5029),
                order(ORDER_B, SegariRouteOrderEnum.ORDER, -6.1726244, 106.9036136, 5029));

        long[][] distances = {
                {0, 0, 0, 0},
                {0, 0, 5812, 5812},
                {0, 5508, 0, 0},
                {0, 5508, 0, 0}};
        long[][] durations = {
                {0, 0, 0, 0},
                {0, 0, 1273, 1273},
                {0, 1230, 0, 0},
                {0, 1230, 0, 0}};

        VrpResultDTO result = VrpWithSpStartAndArbitraryFinish.run(incidentRequest(orders), stub(distances, durations));

        assertThat(sharesRoute(result, ORDER_A, ORDER_B)).isTrue();
    }

    @Test
    @DisplayName("an unroutable SP leg must not wipe the order's time window")
    void unreachableSpLegDoesNotBypassTimeWindow() {
        // Synthetic: SP -> X is unroutable in the duration matrix, but X is reachable from A.
        // Before the guard, the bypass replaced X's 3000 s deadline with 43200 s and X got spliced
        // in behind A at t=6540 with nothing to object.
        long unreachableOrder = 999L;
        List<RouteOrderV2DTO> orders = List.of(
                order(-2L, SegariRouteOrderEnum.DUMMY, 0.0, 0.0, 43200),
                order(-1L, SegariRouteOrderEnum.SP, -6.19742022712038, 106.898010979866, 1200),
                order(ORDER_A, SegariRouteOrderEnum.ORDER, -6.1726244, 106.9036136, 10000),
                order(unreachableOrder, SegariRouteOrderEnum.ORDER, -6.1800000, 106.9100000, 3000));

        long[][] distances = {
                {0, 0, 0, 0},
                {0, 0, 1000, 1000},
                {0, 1000, 0, 1000},
                {0, 1000, 1000, 0}};
        long[][] durations = {
                {0, 0, 0, 0},
                {0, 0, 1000, OSRMRestService.UNREACHABLE},
                {0, 1000, 0, 5000},
                {0, 1000, 5000, 0}};

        VrpResultDTO result = VrpWithSpStartAndArbitraryFinish.run(incidentRequest(orders), stub(distances, durations));

        assertThat(contains(result, ORDER_A)).isTrue();
        assertThat(contains(result, unreachableOrder)).isFalse();
    }
}
