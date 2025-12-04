package id.segari.ortools.ortool;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;
import id.segari.ortools.dto.route.v2.RouteV2DTO;
import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO;
import id.segari.ortools.dto.route.v2.TspResultDTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.external.LatLong;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.external.OSRMTableResponseDTO;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class TspWithSpStartAndArbitraryFinish {

    // Node indices
    private static final int DUMMY_INDEX = 0;
    private static final int SP_INDEX = 1;
    private static final int ORDER_START_INDEX = 2;

    // TSP configuration
    private static final int VEHICLE_COUNT = 1;

    // Penalties
    private static final long DROP_PENALTY = 100_000L;
    private static final long MANDATORY_PENALTY = 1_000_000_000L;

    // Time constraints
    private static final int TIME_SLACK = 120;
    private static final int MAX_ROUTE_TIME = 86400;
    private static final int TIME_WINDOW_BYPASS = 43200;

    public static TspResultDTO run(RouteV2DTO dto, OSRMRestService osrmRestService) {
        validateInput(dto);

        final List<RouteOrderV2DTO> orders = dto.orders();
        final Set<Long> mandatoryOrderIds = getMandatoryOrderIds(dto);
        final Set<Integer> extensionOrderIndices = getExtensionOrderIndices(orders);
        final boolean hasExtensions = !extensionOrderIndices.isEmpty();

        final OSRMTableResponseDTO tableMatrix = fetchTableMatrix(orders, osrmRestService);
        final long[][] distanceMatrix = preprocessDistanceMatrix(tableMatrix.distances(), orders, dto, hasExtensions);
        final long[][] timeWindows = initializeTimeWindows(orders);
        final long[][] durationMatrix = preprocessDurationMatrix(tableMatrix.durations(), orders, timeWindows);

        final RoutingIndexManager manager = createRoutingManager(distanceMatrix);
        final RoutingModel routing = new RoutingModel(manager);

        setupDimensions(routing, manager, orders, distanceMatrix, durationMatrix, timeWindows, extensionOrderIndices, dto, hasExtensions);
        addPenaltyAndDropVisit(routing, manager, orders, mandatoryOrderIds);

        Assignment solution = findSolution(routing);
        return new TspResultDTO(extractResult(routing, manager, solution, orders, mandatoryOrderIds));
    }

    // ==================== Validation ====================

    private static void validateInput(RouteV2DTO dto) {
        if (dto.orders().size() <= 1) throw SegariRoutingErrors.emptyOrder();
        if (!isNodeType(dto.orders().get(DUMMY_INDEX), SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY)) {
            throw SegariRoutingErrors.indexZeroNotDummy();
        }
        if (!isNodeType(dto.orders().get(SP_INDEX), SegariRouteOrderDTO.SegariRouteOrderEnum.SP)) {
            throw SegariRoutingErrors.indexOneNotSp();
        }
        if (dto.maxTotalDistanceWithNonExtensionInMeter() <= 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("maxTotalDistanceWithNonExtensionInMeter");
        }
        if (dto.maxTotalDistanceWithExtensionInMeter() <= 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("maxTotalDistanceWithExtensionInMeter");
        }
        if (dto.maxOrderCountWithExtension() <= 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("maxOrderCountWithExtension");
        }
        if (dto.maxOrderCountWithNonExtension() <= 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("maxOrderCountWithNonExtension");
        }
        if (dto.maxOrderCountWithNonExtension() > dto.maxOrderCountWithExtension()) {
            throw SegariRoutingErrors.invalidRoutingParameter("maxOrderCountWithNonExtension cannot exceed maxOrderCountWithExtension");
        }
        if (dto.maxTotalDistanceWithNonExtensionInMeter() > dto.maxTotalDistanceWithExtensionInMeter()) {
            throw SegariRoutingErrors.invalidRoutingParameter("maxTotalDistanceWithNonExtensionInMeter cannot exceed maxTotalDistanceWithExtensionInMeter");
        }
    }

    // ==================== Data Preparation ====================

    private static Set<Long> getMandatoryOrderIds(RouteV2DTO dto) {
        return CollectionUtils.isEmpty(dto.mandatoryOrders()) ? Collections.emptySet() : dto.mandatoryOrders();
    }

    private static OSRMTableResponseDTO fetchTableMatrix(List<RouteOrderV2DTO> orders, OSRMRestService osrmRestService) {
        List<LatLong> latLongs = orders.stream()
                .map(order -> new LatLong(order.latitude(), order.longitude()))
                .toList();
        return osrmRestService.getMatrix(latLongs);
    }

    private static long[][] initializeTimeWindows(List<RouteOrderV2DTO> orders) {
        long[][] timeWindows = new long[orders.size()][2];
        for (int i = 0; i < orders.size(); i++) {
            timeWindows[i][0] = 0;
            timeWindows[i][1] = orders.get(i).maxTimeWindow();
        }
        return timeWindows;
    }

    // ==================== Matrix Preprocessing ====================

    private static long[][] preprocessDistanceMatrix(long[][] distanceMatrix, List<RouteOrderV2DTO> orders,
                                                      RouteV2DTO dto, boolean hasExtensions) {
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
                distanceMatrix[i][j] = basicValue > maxAllowedDistance ? prohibitiveDistance : basicValue;
            }
        }
        return distanceMatrix;
    }

    private static long[][] preprocessDurationMatrix(long[][] durationMatrix, List<RouteOrderV2DTO> orders, long[][] timeWindows) {
        int length = orders.size();
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (isDummyNode(i, orders) || isDummyNode(j, orders)) {
                    durationMatrix[i][j] = 0;
                }
                if (isSpNode(i, orders) && durationMatrix[i][j] > timeWindows[j][1]) {
                    timeWindows[j][1] = TIME_WINDOW_BYPASS;
                }
            }
        }
        return durationMatrix;
    }

    // ==================== Node Type Checking ====================

    private static boolean isNodeType(RouteOrderV2DTO order, SegariRouteOrderDTO.SegariRouteOrderEnum type) {
        return type.equals(order.type());
    }

    private static boolean isDummyNode(int index, List<RouteOrderV2DTO> orders) {
        return isNodeType(orders.get(index), SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY);
    }

    private static boolean isSpNode(int index, List<RouteOrderV2DTO> orders) {
        return isNodeType(orders.get(index), SegariRouteOrderDTO.SegariRouteOrderEnum.SP);
    }

    private static boolean isSpecialNode(int index, List<RouteOrderV2DTO> orders) {
        return isDummyNode(index, orders) || isSpNode(index, orders);
    }

    private static boolean isExtensionEdge(int i, int j, List<RouteOrderV2DTO> orders) {
        return Boolean.TRUE.equals(orders.get(i).isExtension()) || Boolean.TRUE.equals(orders.get(j).isExtension());
    }

    private static Set<Integer> getExtensionOrderIndices(List<RouteOrderV2DTO> orders) {
        Set<Integer> indices = new HashSet<>();
        for (int i = ORDER_START_INDEX; i < orders.size(); i++) {
            if (Boolean.TRUE.equals(orders.get(i).isExtension())) {
                indices.add(i);
            }
        }
        return indices;
    }

    // ==================== Routing Setup ====================

    private static RoutingIndexManager createRoutingManager(long[][] distanceMatrix) {
        if (distanceMatrix.length == 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("distanceMatrix");
        }
        int[] start = arrayOf(SP_INDEX);
        int[] finish = arrayOf(DUMMY_INDEX);
        return new RoutingIndexManager(distanceMatrix.length, VEHICLE_COUNT, start, finish);
    }

    private static void setupDimensions(RoutingModel routing, RoutingIndexManager manager,
                                        List<RouteOrderV2DTO> orders, long[][] distanceMatrix,
                                        long[][] durationMatrix, long[][] timeWindows,
                                        Set<Integer> extensionOrderIndices, RouteV2DTO dto, boolean hasExtensions) {
        long[] orderDemands = createOrderDemands(orders.size());

        if (hasExtensions) {
            addDistanceDimension(routing, manager, dto.maxTotalDistanceWithExtensionInMeter(), distanceMatrix);
            addMaxOrderCountDimension(routing, manager, orderDemands, dto.maxOrderCountWithExtension());
            addNonExtensionCountDimension(routing, manager, extensionOrderIndices, dto.maxOrderCountWithNonExtension());
            addNonExtensionDistanceDimension(routing, manager, extensionOrderIndices, dto.maxTotalDistanceWithNonExtensionInMeter(), distanceMatrix);
        } else {
            addDistanceDimension(routing, manager, dto.maxTotalDistanceWithNonExtensionInMeter(), distanceMatrix);
            addMaxOrderCountDimension(routing, manager, orderDemands, dto.maxOrderCountWithNonExtension());
        }

        addTimeWindowDimension(routing, manager, durationMatrix, timeWindows);
    }

    // ==================== Dimension Methods ====================

    private static void addDistanceDimension(RoutingModel routing, RoutingIndexManager manager,
                                             int maxTotalDistance, long[][] distanceMatrix) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return distanceMatrix[fromNode][toNode];
        });
        routing.setArcCostEvaluatorOfAllVehicles(callback);
        routing.addDimension(callback, 0, maxTotalDistance, true, "Distance");
    }

    private static void addMaxOrderCountDimension(RoutingModel routing, RoutingIndexManager manager,
                                                   long[] orderDemands, int maxOrderCount) {
        int callback = routing.registerUnaryTransitCallback(fromIndex -> {
            int fromNode = manager.indexToNode(fromIndex);
            return orderDemands[fromNode];
        });
        long[] capacities = new long[]{maxOrderCount};
        routing.addDimensionWithVehicleCapacity(callback, 0, capacities, true, "MaxOrderCount");
    }

    private static void addNonExtensionCountDimension(RoutingModel routing, RoutingIndexManager manager,
                                                       Set<Integer> extensionOrderIndices, int maxNonExtensionCount) {
        int callback = routing.registerUnaryTransitCallback(fromIndex -> {
            int fromNode = manager.indexToNode(fromIndex);
            if (fromNode == DUMMY_INDEX || fromNode == SP_INDEX) return 0;
            return extensionOrderIndices.contains(fromNode) ? 0 : 1;
        });
        routing.addDimension(callback, 0, maxNonExtensionCount, true, "NonExtensionCount");
    }

    private static void addNonExtensionDistanceDimension(RoutingModel routing, RoutingIndexManager manager,
                                                          Set<Integer> extensionOrderIndices,
                                                          int maxNonExtensionDistance, long[][] distanceMatrix) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);

            if (fromNode == DUMMY_INDEX || toNode == DUMMY_INDEX) return 0;
            if (fromNode == SP_INDEX || toNode == SP_INDEX) return 0;
            if (extensionOrderIndices.contains(fromNode) || extensionOrderIndices.contains(toNode)) return 0;

            return distanceMatrix[fromNode][toNode];
        });
        routing.addDimension(callback, 0, maxNonExtensionDistance, true, "NonExtensionDistance");
    }

    private static void addTimeWindowDimension(RoutingModel routing, RoutingIndexManager manager,
                                                long[][] durationMatrix, long[][] timeWindows) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return durationMatrix[fromNode][toNode];
        });
        routing.addDimension(callback, TIME_SLACK, MAX_ROUTE_TIME, false, "Time");

        RoutingDimension timeDimension = routing.getMutableDimension("Time");
        for (int i = ORDER_START_INDEX; i < durationMatrix.length; i++) {
            long index = manager.nodeToIndex(i);
            timeDimension.cumulVar(index).setRange(timeWindows[i][0], timeWindows[i][1]);
        }
    }

    private static void addPenaltyAndDropVisit(RoutingModel routing, RoutingIndexManager manager,
                                                List<RouteOrderV2DTO> orders, Set<Long> mandatoryOrderIds) {
        for (int i = ORDER_START_INDEX; i < orders.size(); i++) {
            long orderId = orders.get(i).id();
            long penalty = mandatoryOrderIds.contains(orderId) ? MANDATORY_PENALTY : DROP_PENALTY;
            routing.addDisjunction(new long[]{manager.nodeToIndex(i)}, penalty);
        }
    }

    // ==================== Solution ====================

    private static Assignment findSolution(RoutingModel routing) {
        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.CHRISTOFIDES)
                .setTimeLimit(Duration.newBuilder().setSeconds(60).build())
                .setLogSearch(true)
                .build();
        return routing.solveWithParameters(searchParameters);
    }

    private static List<Long> extractResult(RoutingModel routing, RoutingIndexManager manager,
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

    private static ArrayList<Long> extractVehicleRoute(RoutingModel routing, RoutingIndexManager manager,
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

    private static long[] createOrderDemands(int orderCount) {
        long[] demands = new long[orderCount];
        for (int i = ORDER_START_INDEX; i < orderCount; i++) {
            demands[i] = 1;
        }
        return demands;
    }

    private static int[] arrayOf(int value) {
        int[] array = new int[TspWithSpStartAndArbitraryFinish.VEHICLE_COUNT];
        Arrays.fill(array, value);
        return array;
    }

    static {
        Loader.loadNativeLibraries();
    }
}
