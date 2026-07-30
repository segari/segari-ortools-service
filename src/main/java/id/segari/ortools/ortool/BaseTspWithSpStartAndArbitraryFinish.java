package id.segari.ortools.ortool;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import id.segari.ortools.dto.route.RoutePreprocessingParams;
import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO;
import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.external.LatLong;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.external.OSRMTableResponseDTO;
import org.slf4j.Logger;

import java.util.*;

public abstract class BaseTspWithSpStartAndArbitraryFinish {

    // ==================== Constants ====================

    protected static final int DUMMY_INDEX = 0;
    protected static final int SP_INDEX = 1;
    protected static final int ORDER_START_INDEX = 2;

    protected static final int VEHICLE_COUNT = 1;

    protected static final long DROP_PENALTY = 1_000_000L;
    protected static final long MANDATORY_PENALTY = 1_000_000_000L;

    protected static final int MAX_ROUTE_TIME = 86400;
    protected static final int TIME_WINDOW_BYPASS = 43200;

    // ==================== Node Type Checking ====================

    protected static boolean isNodeType(RouteOrderV2DTO order, SegariRouteOrderDTO.SegariRouteOrderEnum type) {
        return type.equals(order.type());
    }

    protected static boolean isDummyNode(int index, List<RouteOrderV2DTO> orders) {
        return isNodeType(orders.get(index), SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY);
    }

    protected static boolean isSpNode(int index, List<RouteOrderV2DTO> orders) {
        return isNodeType(orders.get(index), SegariRouteOrderDTO.SegariRouteOrderEnum.SP);
    }

    protected static boolean isOrderNode(int index, List<RouteOrderV2DTO> orders) {
        return isNodeType(orders.get(index), SegariRouteOrderDTO.SegariRouteOrderEnum.ORDER);
    }

    protected static boolean isSpecialNode(int index, List<RouteOrderV2DTO> orders) {
        return isDummyNode(index, orders) || isSpNode(index, orders);
    }

    protected static boolean isExtensionEdge(int i, int j, List<RouteOrderV2DTO> orders) {
        return Boolean.TRUE.equals(orders.get(i).isExtension()) || Boolean.TRUE.equals(orders.get(j).isExtension());
    }

    /**
     * True when OSRM could not route this cell. Values keep their {@link OSRMRestService#UNREACHABLE}
     * magnitude after overhead/slack are added, so compare with {@code >=}.
     */
    protected static boolean isUnreachable(long osrmValue) {
        return osrmValue >= OSRMRestService.UNREACHABLE;
    }

    /**
     * Whether an order-to-order edge must be replaced by a prohibitive distance.
     * <p>
     * Besides the configured cap, a zero distance between two <em>distinct</em> coordinates is
     * rejected: that cannot happen on a real road network, so it means the value was defaulted or is
     * garbage. Left as-is it would be the cheapest possible arc and would pass every cap.
     * Two orders at the very same coordinate legitimately measure zero and stay batchable.
     */
    protected static boolean isForbiddenOrderEdge(int i, int j, long distance, int maxAllowedDistance,
                                                  List<RouteOrderV2DTO> orders) {
        if (i == j) return false;
        if (distance > maxAllowedDistance) return true;
        return distance == 0 && !isSameCoordinate(orders.get(i), orders.get(j));
    }

    protected static boolean isSameCoordinate(RouteOrderV2DTO a, RouteOrderV2DTO b) {
        return Objects.equals(a.latitude(), b.latitude()) && Objects.equals(a.longitude(), b.longitude());
    }

    /**
     * Warns about orders OSRM could not route. Must be called on the raw matrices, before
     * preprocessing rewrites unreachable cells into prohibitive distances.
     */
    protected static void warnUnreachableOrders(Logger log, List<RouteOrderV2DTO> orders,
                                                long[][] distanceMatrix, long[][] durationMatrix) {
        List<Long> unreachable = new ArrayList<>();
        for (int i = ORDER_START_INDEX; i < orders.size(); i++) {
            if (hasUnreachableEdge(i, distanceMatrix) || hasUnreachableEdge(i, durationMatrix)) {
                unreachable.add(orders.get(i).id());
            }
        }
        if (!unreachable.isEmpty()) {
            log.warn("OSRM could not route {} order(s); they will be penalised or dropped: {}",
                    unreachable.size(), unreachable);
        }
    }

    private static boolean hasUnreachableEdge(int node, long[][] matrix) {
        if (matrix == null || matrix.length <= node) return false;
        for (int j = 0; j < matrix.length; j++) {
            if (isUnreachable(matrix[node][j]) || isUnreachable(matrix[j][node])) return true;
        }
        return false;
    }

    protected static Set<Integer> getExtensionOrderIndices(List<RouteOrderV2DTO> orders) {
        Set<Integer> indices = new HashSet<>();
        for (int i = ORDER_START_INDEX; i < orders.size(); i++) {
            if (Boolean.TRUE.equals(orders.get(i).isExtension())) {
                indices.add(i);
            }
        }
        return indices;
    }

    // ==================== Data Preparation ====================

    protected static OSRMTableResponseDTO fetchTableMatrix(RoutePreprocessingParams dto, OSRMRestService osrmRestService) {
        List<LatLong> latLongs = dto.orders().stream()
                .map(order -> new LatLong(order.latitude(), order.longitude()))
                .toList();
        return osrmRestService.getMatrixWithScaleFactor(latLongs, dto.scaleFactor());
    }

    protected static long[][] initializeTimeWindows(List<RouteOrderV2DTO> orders) {
        long[][] timeWindows = new long[orders.size()][2];
        for (int i = 0; i < orders.size(); i++) {
            timeWindows[i][0] = 0;
            timeWindows[i][1] = orders.get(i).maxTimeWindow();
        }
        return timeWindows;
    }

    // ==================== Matrix Preprocessing ====================

    /**
     * Rewrites every order-to-order edge that breaches its cap into a prohibitive distance, so the
     * distance dimension rejects it. DUMMY edges are zeroed; SP edges are left untouched.
     */
    protected static long[][] preprocessDistanceMatrix(long[][] distanceMatrix, List<RouteOrderV2DTO> orders,
                                                       RoutePreprocessingParams dto, boolean hasExtensions) {
        int length = orders.size();
        int maxDistanceNonExt = dto.maxDistanceBetweenOrderToNonExtensionInMeter();
        int maxDistanceExt = dto.maxDistanceBetweenOrderToExtensionInMeter();
        int prohibitiveDistance = (hasExtensions ? dto.maxTotalDistanceWithExtensionInMeter() : dto.maxTotalDistanceWithNonExtensionInMeter()) + 1;

        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (isSpecialNode(i, orders) || isSpecialNode(j, orders)) {
                    if (isDummyNode(i, orders) || isDummyNode(j, orders)) {
                        distanceMatrix[i][j] = 0;
                    }
                    continue;
                }

                long basicValue = distanceMatrix[i][j];
                int maxAllowedDistance = (hasExtensions && isExtensionEdge(i, j, orders)) ? maxDistanceExt : maxDistanceNonExt;
                distanceMatrix[i][j] = isForbiddenOrderEdge(i, j, basicValue, maxAllowedDistance, orders)
                        ? prohibitiveDistance
                        : basicValue;
            }
        }
        return distanceMatrix;
    }

    /**
     * Folds the fixed overhead (SP to first order) and the inter-stop slack into the OSRM durations,
     * and relaxes the deadline of any order that cannot be served on time even as the first stop.
     */
    protected static long[][] preprocessDurationMatrix(long[][] durationMatrix, List<RouteOrderV2DTO> orders,
                                                       long[][] timeWindows, RoutePreprocessingParams dto) {
        int length = orders.size();
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (isDummyNode(i, orders) || isDummyNode(j, orders)) {
                    durationMatrix[i][j] = 0;
                    continue;
                }
                if (isSpNode(i, orders) && isOrderNode(j, orders)) {
                    durationMatrix[i][j] += dto.overheadTimeInSecond();
                }
                if (isOrderNode(i, orders) && isOrderNode(j, orders) && i != j) {
                    durationMatrix[i][j] += dto.slackTimeInSecond();
                }
                // An order that cannot be reached in time even as the first stop is already doomed to
                // be late, so its deadline is dropped rather than allowed to veto batching. Skip this
                // when OSRM could not route SP -> j at all: that is missing data, not lateness, and
                // relaxing the window would let the order be spliced mid-route with no deadline.
                if (isSpNode(i, orders)
                        && !isUnreachable(durationMatrix[i][j])
                        && durationMatrix[i][j] > timeWindows[j][1]) {
                    timeWindows[j][1] = TIME_WINDOW_BYPASS;
                }
            }
        }
        return durationMatrix;
    }

    // ==================== Routing Setup ====================

    protected static RoutingIndexManager createRoutingManager(long[][] distanceMatrix) {
        if (distanceMatrix.length == 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("distanceMatrix");
        }
        int[] start = arrayOf(SP_INDEX);
        int[] finish = arrayOf(DUMMY_INDEX);
        return new RoutingIndexManager(distanceMatrix.length, VEHICLE_COUNT, start, finish);
    }

    // ==================== Dimension Methods ====================

    protected static void addDistanceDimension(RoutingModel routing, RoutingIndexManager manager,
                                               int maxTotalDistance, long[][] distanceMatrix) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return distanceMatrix[fromNode][toNode];
        });
        routing.setArcCostEvaluatorOfAllVehicles(callback);
        routing.addDimension(callback, 0, maxTotalDistance, true, "Distance");
    }

    protected static void addMaxOrderCountDimension(RoutingModel routing, RoutingIndexManager manager,
                                                    long[] orderDemands, int maxOrderCount) {
        int callback = routing.registerUnaryTransitCallback(fromIndex -> {
            int fromNode = manager.indexToNode(fromIndex);
            return orderDemands[fromNode];
        });
        long[] capacities = new long[]{maxOrderCount};
        routing.addDimensionWithVehicleCapacity(callback, 0, capacities, true, "MaxOrderCount");
    }

    protected static void addNonExtensionCountDimension(RoutingModel routing, RoutingIndexManager manager,
                                                        Set<Integer> extensionOrderIndices, int maxNonExtensionCount) {
        int callback = routing.registerUnaryTransitCallback(fromIndex -> {
            int fromNode = manager.indexToNode(fromIndex);
            if (fromNode == DUMMY_INDEX || fromNode == SP_INDEX) return 0;
            return extensionOrderIndices.contains(fromNode) ? 0 : 1;
        });
        routing.addDimension(callback, 0, maxNonExtensionCount, true, "NonExtensionCount");
    }

    protected static void addNonExtensionDistanceDimension(RoutingModel routing, RoutingIndexManager manager,
                                                           Set<Integer> extensionOrderIndices,
                                                           int maxNonExtensionDistance, long[][] distanceMatrix) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);

            if (fromNode == DUMMY_INDEX || toNode == DUMMY_INDEX) return 0;
            if (extensionOrderIndices.contains(fromNode) || extensionOrderIndices.contains(toNode)) return 0;

            return distanceMatrix[fromNode][toNode];
        });
        routing.addDimension(callback, 0, maxNonExtensionDistance, true, "NonExtensionDistance");
    }

    protected static void addPenaltyAndDropVisit(RoutingModel routing, RoutingIndexManager manager,
                                                 List<RouteOrderV2DTO> orders, Set<Long> mandatoryOrderIds) {
        for (int i = ORDER_START_INDEX; i < orders.size(); i++) {
            long orderId = orders.get(i).id();
            long penalty = mandatoryOrderIds.contains(orderId) ? MANDATORY_PENALTY : DROP_PENALTY;
            routing.addDisjunction(new long[]{manager.nodeToIndex(i)}, penalty);
        }
    }

    // ==================== Solution ====================

    protected static Assignment findSolution(RoutingModel routing, FirstSolutionStrategy.Value strategy) {
        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(strategy)
                .setTimeLimit(Duration.newBuilder().setSeconds(60).build())
                .build();
        return routing.solveWithParameters(searchParameters);
    }

    protected static List<Long> extractResult(RoutingModel routing, RoutingIndexManager manager,
                                              Assignment solution, List<RouteOrderV2DTO> orders,
                                              Set<Long> mandatoryOrderIds) {
        if (Objects.isNull(solution)) return Collections.emptyList();

        List<ArrayList<Long>> results = new ArrayList<>();
        for (int vehicle = 0; vehicle < VEHICLE_COUNT; vehicle++) {
            final ArrayList<Long> route = extractVehicleRoute(routing, manager, solution, orders, vehicle);
            results.add(route);
        }

        if (!mandatoryOrderIds.isEmpty()) {
            if (!new HashSet<>(results.getFirst()).containsAll(mandatoryOrderIds)) {
                return Collections.emptyList();
            }
        }
        return results.isEmpty() ? Collections.emptyList() : results.getFirst();
    }

    protected static ArrayList<Long> extractVehicleRoute(RoutingModel routing, RoutingIndexManager manager,
                                                         Assignment solution, List<RouteOrderV2DTO> orders, int vehicle) {
        ArrayList<Long> route = new ArrayList<>();
        long index = routing.start(vehicle);

        while (!routing.isEnd(index)) {
            int nodeIndex = manager.indexToNode(index);
            long orderId = orders.get(nodeIndex).id();
            if (orderId != -1L && orderId != -2L) {
                route.add(orderId);
            }
            index = solution.value(routing.nextVar(index));
        }
        return route;
    }

    // ==================== Utility Methods ====================

    protected static long[] createOrderDemands(int orderCount) {
        long[] demands = new long[orderCount];
        for (int i = ORDER_START_INDEX; i < orderCount; i++) {
            demands[i] = 1;
        }
        return demands;
    }

    protected static int[] arrayOf(int value) {
        int[] array = new int[VEHICLE_COUNT];
        Arrays.fill(array, value);
        return array;
    }

    static {
        Loader.loadNativeLibraries();
    }
}
