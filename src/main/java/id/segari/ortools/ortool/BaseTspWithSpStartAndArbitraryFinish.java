package id.segari.ortools.ortool;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO;
import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;
import id.segari.ortools.error.SegariRoutingErrors;

import java.util.*;

public abstract class BaseTspWithSpStartAndArbitraryFinish {

    // ==================== Constants ====================

    protected static final int DUMMY_INDEX = 0;
    protected static final int SP_INDEX = 1;
    protected static final int ORDER_START_INDEX = 2;

    protected static final int VEHICLE_COUNT = 1;

    protected static final long DROP_PENALTY = 1_000_000L;
    protected static final long MANDATORY_PENALTY = 1000_000_000L;

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

    protected static boolean isSpecialNode(int index, List<RouteOrderV2DTO> orders) {
        return isDummyNode(index, orders) || isSpNode(index, orders);
    }

    protected static boolean isExtensionEdge(int i, int j, List<RouteOrderV2DTO> orders) {
        return Boolean.TRUE.equals(orders.get(i).isExtension()) || Boolean.TRUE.equals(orders.get(j).isExtension());
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

    protected static long[][] initializeTimeWindows(List<RouteOrderV2DTO> orders) {
        long[][] timeWindows = new long[orders.size()][2];
        for (int i = 0; i < orders.size(); i++) {
            timeWindows[i][0] = 0;
            timeWindows[i][1] = orders.get(i).maxTimeWindow();
        }
        return timeWindows;
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

    protected static Assignment findSolution(RoutingModel routing) {
        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.CHRISTOFIDES)
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
