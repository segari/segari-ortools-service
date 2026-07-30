package id.segari.ortools.ortool;

import com.google.ortools.constraintsolver.*;
import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO;
import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;
import id.segari.ortools.dto.route.v4.RouteV4DTO;
import id.segari.ortools.dto.route.v4.VrpResultDTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.external.OSRMTableResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.IntStream;

public class VrpWithSpStartAndArbitraryFinish extends BaseTspWithSpStartAndArbitraryFinish {

    private static final Logger log = LoggerFactory.getLogger(VrpWithSpStartAndArbitraryFinish.class);

    private static final int GLOBAL_SPAN_COST_COEFFICIENT = 100;

    public static VrpResultDTO run(RouteV4DTO dto, OSRMRestService osrmRestService) {
        validateInput(dto);

        final List<RouteOrderV2DTO> orders = dto.orders();
        final Set<Integer> extensionOrderIndices = getExtensionOrderIndices(orders);
        final boolean hasExtensions = !extensionOrderIndices.isEmpty();
        final int vehicleCount = dto.vehicleNumber();
        final int vehicleWithExtensionNumber = dto.vehicleWithExtensionNumber();

        final OSRMTableResponseDTO tableMatrix = fetchTableMatrix(dto, osrmRestService);
        warnUnreachableOrders(log, orders, tableMatrix.distances(), tableMatrix.durations());
        final long[][] distanceMatrix = preprocessDistanceMatrix(tableMatrix.distances(), orders, dto, hasExtensions);
        final long[][] timeWindows = initializeTimeWindows(orders);
        final long[][] durationMatrix = preprocessDurationMatrix(tableMatrix.durations(), orders, timeWindows, dto);

        final int nodeCount = orders.size();
        final int[] starts = new int[vehicleCount];
        final int[] finishes = new int[vehicleCount];
        Arrays.fill(starts, SP_INDEX);
        Arrays.fill(finishes, DUMMY_INDEX);

        final RoutingIndexManager manager = new RoutingIndexManager(nodeCount, vehicleCount, starts, finishes);
        final RoutingModel routing = new RoutingModel(manager);
        routing.setFixedCostOfAllVehicles(200_000L);

        setupDimensions(routing, manager, orders, distanceMatrix, durationMatrix, timeWindows,
                extensionOrderIndices, dto, hasExtensions, vehicleCount, vehicleWithExtensionNumber);

        if (hasExtensions && vehicleWithExtensionNumber < vehicleCount) {
            restrictExtensionOrdersToExtensionVehicles(routing, manager, extensionOrderIndices, vehicleWithExtensionNumber);
        }

        addPenaltyAndDropVisit(routing, manager, orders, extensionOrderIndices, vehicleWithExtensionNumber);

        Assignment solution = findSolution(routing, FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC);
        return extractVrpResult(routing, manager, solution, orders, vehicleCount);
    }

    // ==================== Validation ====================

    private static void validateInput(RouteV4DTO dto) {
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
        if (dto.overheadTimeInSecond() == null || dto.overheadTimeInSecond() < 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("overheadTimeInSecond");
        }
        if (dto.slackTimeInSecond() == null || dto.slackTimeInSecond() < 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("slackTimeInSecond");
        }
        if (dto.vehicleNumber() == null || dto.vehicleNumber() <= 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("vehicleNumber");
        }
        if (dto.vehicleWithExtensionNumber() == null || dto.vehicleWithExtensionNumber() < 0) {
            throw SegariRoutingErrors.invalidRoutingParameter("vehicleWithExtensionNumber");
        }
        if (dto.vehicleWithExtensionNumber() > dto.vehicleNumber()) {
            throw SegariRoutingErrors.invalidRoutingParameter("vehicleWithExtensionNumber cannot exceed vehicleNumber");
        }
    }

    // ==================== Dimensions Setup ====================

    private static void setupDimensions(RoutingModel routing, RoutingIndexManager manager,
                                        List<RouteOrderV2DTO> orders, long[][] distanceMatrix,
                                        long[][] durationMatrix, long[][] timeWindows,
                                        Set<Integer> extensionOrderIndices, RouteV4DTO dto,
                                        boolean hasExtensions, int vehicleCount, int vehicleWithExtensionNumber) {
        long[] orderDemands = createOrderDemands(orders.size());

        addDistanceDimensionVrp(routing, manager, distanceMatrix, dto, vehicleCount, vehicleWithExtensionNumber);
        addMaxOrderCountDimensionVrp(routing, manager, orderDemands, dto, vehicleCount, vehicleWithExtensionNumber);

        if (hasExtensions) {
            addNonExtensionCountDimensionVrp(routing, manager, extensionOrderIndices,
                    dto.maxOrderCountWithNonExtension(), vehicleCount);
            addNonExtensionDistanceDimensionVrp(routing, manager, extensionOrderIndices,
                    dto.maxTotalDistanceWithNonExtensionInMeter(), distanceMatrix, vehicleCount);
        }

        addTimeWindowDimension(routing, manager, durationMatrix, timeWindows);
    }

    private static void addDistanceDimensionVrp(RoutingModel routing, RoutingIndexManager manager,
                                                long[][] distanceMatrix, RouteV4DTO dto,
                                                int vehicleCount, int vehicleWithExtensionNumber) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return distanceMatrix[fromNode][toNode];
        });
        routing.setArcCostEvaluatorOfAllVehicles(callback);

        long[] capacities = new long[vehicleCount];
        for (int v = 0; v < vehicleCount; v++) {
            capacities[v] = v < vehicleWithExtensionNumber
                    ? dto.maxTotalDistanceWithExtensionInMeter()
                    : dto.maxTotalDistanceWithNonExtensionInMeter();
        }
        routing.addDimensionWithVehicleCapacity(callback, 0, capacities, true, "Distance");
    }

    private static void addMaxOrderCountDimensionVrp(RoutingModel routing, RoutingIndexManager manager,
                                                     long[] orderDemands, RouteV4DTO dto,
                                                     int vehicleCount, int vehicleWithExtensionNumber) {
        int callback = routing.registerUnaryTransitCallback(fromIndex -> {
            int fromNode = manager.indexToNode(fromIndex);
            return orderDemands[fromNode];
        });

        long[] capacities = new long[vehicleCount];
        for (int v = 0; v < vehicleCount; v++) {
            capacities[v] = v < vehicleWithExtensionNumber
                    ? dto.maxOrderCountWithExtension()
                    : dto.maxOrderCountWithNonExtension();
        }
        routing.addDimensionWithVehicleCapacity(callback, 0, capacities, true, "MaxOrderCount");
    }

    private static void addNonExtensionCountDimensionVrp(RoutingModel routing, RoutingIndexManager manager,
                                                         Set<Integer> extensionOrderIndices,
                                                         int maxNonExtensionCount, int vehicleCount) {
        int callback = routing.registerUnaryTransitCallback(fromIndex -> {
            int fromNode = manager.indexToNode(fromIndex);
            if (fromNode == DUMMY_INDEX || fromNode == SP_INDEX) return 0;
            return extensionOrderIndices.contains(fromNode) ? 0 : 1;
        });

        long[] capacities = new long[vehicleCount];
        Arrays.fill(capacities, maxNonExtensionCount);
        routing.addDimensionWithVehicleCapacity(callback, 0, capacities, true, "NonExtensionCount");
    }

    private static void addNonExtensionDistanceDimensionVrp(RoutingModel routing, RoutingIndexManager manager,
                                                            Set<Integer> extensionOrderIndices,
                                                            int maxNonExtensionDistance, long[][] distanceMatrix,
                                                            int vehicleCount) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);

            if (fromNode == DUMMY_INDEX || toNode == DUMMY_INDEX) return 0;
            if (extensionOrderIndices.contains(fromNode) || extensionOrderIndices.contains(toNode)) return 0;

            return distanceMatrix[fromNode][toNode];
        });

        long[] capacities = new long[vehicleCount];
        Arrays.fill(capacities, maxNonExtensionDistance);
        routing.addDimensionWithVehicleCapacity(callback, 0, capacities, true, "NonExtensionDistance");
    }

    private static void addTimeWindowDimension(RoutingModel routing, RoutingIndexManager manager,
                                               long[][] durationMatrix, long[][] timeWindows) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return durationMatrix[fromNode][toNode];
        });
        routing.addDimension(callback, 0, MAX_ROUTE_TIME, false, "Time");

        RoutingDimension timeDimension = routing.getMutableDimension("Time");
        for (int i = ORDER_START_INDEX; i < durationMatrix.length; i++) {
            long index = manager.nodeToIndex(i);
            timeDimension.cumulVar(index).setRange(timeWindows[i][0], timeWindows[i][1]);
        }

        timeDimension.setGlobalSpanCostCoefficient(GLOBAL_SPAN_COST_COEFFICIENT);
    }

    // ==================== Extension Order Restriction ====================

    private static void restrictExtensionOrdersToExtensionVehicles(RoutingModel routing, RoutingIndexManager manager,
                                                                    Set<Integer> extensionOrderIndices,
                                                                    int vehicleWithExtensionNumber) {
        int[] extensionVehicleIndices = IntStream.range(0, vehicleWithExtensionNumber).toArray();
        for (int orderIndex : extensionOrderIndices) {
            routing.setAllowedVehiclesForIndex(extensionVehicleIndices, manager.nodeToIndex(orderIndex));
        }
    }

    // ==================== Penalty ====================

    private static void addPenaltyAndDropVisit(RoutingModel routing, RoutingIndexManager manager,
                                               List<RouteOrderV2DTO> orders, Set<Integer> extensionOrderIndices,
                                               int vehicleWithExtensionNumber) {
        for (int i = ORDER_START_INDEX; i < orders.size(); i++) {
            long penalty = (vehicleWithExtensionNumber == 0 && extensionOrderIndices.contains(i)) ? 0 : DROP_PENALTY;
            routing.addDisjunction(new long[]{manager.nodeToIndex(i)}, penalty);
        }
    }

    // ==================== Result Extraction ====================

    private static VrpResultDTO extractVrpResult(RoutingModel routing, RoutingIndexManager manager,
                                                 Assignment solution, List<RouteOrderV2DTO> orders, int vehicleCount) {
        if (Objects.isNull(solution)) return new VrpResultDTO(Collections.emptyList());

        List<List<Long>> results = new ArrayList<>();
        for (int vehicle = 0; vehicle < vehicleCount; vehicle++) {
            ArrayList<Long> route = extractVehicleRoute(routing, manager, solution, orders, vehicle);
            if (!route.isEmpty()) {
                results.add(route);
            }
        }
        return new VrpResultDTO(results);
    }
}
