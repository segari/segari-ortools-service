package id.segari.ortools.ortool;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import id.segari.ortools.dto.RouteDTO;
import id.segari.ortools.dto.RouteResultDTO;
import id.segari.ortools.dto.SegariRouteOrderDTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.external.LatLong;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.external.OSRMTableResponseDTO;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class TspWithSpStartAndArbitraryFinish {

    public static RouteResultDTO run(RouteDTO dto, OSRMRestService osrmRestService) {
        if (dto.route().orders().size() <= 1) throw SegariRoutingErrors.emptyOrder();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(dto.route().orders().get(0).type())) throw SegariRoutingErrors.indexZeroNotDummy();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(dto.route().orders().get(1).type())) throw SegariRoutingErrors.indexOneNotSp();

        if (dto.route().maxTotalDistanceInMeter() <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxTotalDistanceInMeter in injectTspAttributes");
        if (dto.route().maxOrderCount() <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxOrderCount in injectTspAttributes");

        final List<SegariRouteOrderDTO> orders = dto.route().orders();
        final int startIndex = 1; // SP
        final int finishIndex = 0; // dummy for arbitrary finish
        final int startOrderIndex = 2; // order start from index 2
        final int length = orders.size();
        final int maxTotalDistanceInMeter = dto.route().maxTotalDistanceInMeter();
        final int maxOrderCount = dto.route().maxOrderCount();
        final int maxNonExtensionCount = 4; // TODO - get from dto
        final int vehicleNumbers = 1; // TSP only use 1 vehicle number
        final Set<Long> mandatoryOrderIds = CollectionUtils.isEmpty(dto.route().mandatoryOrders())
                ? Collections.emptySet()
                : dto.route().mandatoryOrders();
        final long[][] timeWindows = new long[dto.route().orders().size()][2];
        for (int i = 0; i < dto.route().orders().size(); i++) {
            timeWindows[i][0] = 0;
            timeWindows[i][1] = dto.route().orders().get(i).maxTimeWindow();
        }
        final int maxDistanceBetweenOrderInMeter = dto.maxDistanceBetweenOrder();

        final List<LatLong> latLongs = orders.stream()
                .map(order -> new LatLong(order.latitude(), order.longitude()))
                .toList();
        final OSRMTableResponseDTO tableMatrix = osrmRestService.getDistanceMatrix(latLongs);

        // distance matrix
        final long[][] distanceMatrix = tableMatrix.distances();
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (isDummyNode(i, j, orders)){
                    distanceMatrix[i][j] = 0;
                    continue;
                }
                // Don't apply maxDistanceBetweenOrder constraint to SP nodes
                if (isISpNode(i, orders) || isISpNode(j, orders)){
                    continue;
                }
                long basicValue = distanceMatrix[i][j];
                distanceMatrix[i][j] = basicValue > maxDistanceBetweenOrderInMeter ? maxTotalDistanceInMeter + 1 : basicValue;
            }
        }

        // duration matrix
        long[][] durationMatrix = tableMatrix.durations();
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (isDummyNode(i, j, orders)){
                    durationMatrix[i][j] = 0;
                }
                if (isISpNode(i, orders) && durationMatrix[i][j] > timeWindows[j][1]) {
                    timeWindows[j][1] = 43200; // bypass time windows by make it extra large
                }
            }
        }

        // max order
        final long[] maxOrderVehicleCapacities = initiateVehicleArray(vehicleNumbers, maxOrderCount);
        final long[] maxOrderDemands = initiateDemandArray(orders.size(), startOrderIndex);
        final int[] start = arrayOf(vehicleNumbers, startIndex);
        final int[] finish = arrayOf(vehicleNumbers, finishIndex);

        final RoutingIndexManager manager = getRoutingIndexManager(distanceMatrix, vehicleNumbers, start, finish);
        final RoutingModel routing = new RoutingModel(manager);

        addDistanceDimension(routing, manager, maxTotalDistanceInMeter, distanceMatrix);
        addMaxOrderCountDimension(routing, manager, maxOrderDemands, maxOrderVehicleCapacities, distanceMatrix, vehicleNumbers);
        addTimeWindowDimension(routing, manager, durationMatrix, timeWindows, startOrderIndex);

        // Only apply dynamic max order constraint if there are extension orders
        Set<Integer> extensionOrderIndices = getExtensionOrderIndices(orders, startOrderIndex);
        if (!extensionOrderIndices.isEmpty()) {
            addDynamicMaxOrderDimension(routing, manager, orders, startOrderIndex, maxOrderCount, maxNonExtensionCount, finishIndex);
        }

        addPenaltyAndDropVisit(routing, manager, startOrderIndex, mandatoryOrderIds, orders);
        Assignment solution = findSolution(routing);
        return new RouteResultDTO(getResult(routing, manager, solution, vehicleNumbers, orders, mandatoryOrderIds));
    }

    private static boolean isDummyNode(int i, int j, List<SegariRouteOrderDTO> orders) {
        return SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(orders.get(i).type()) || SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(orders.get(j).type());
    }

    private static boolean isISpNode(int i, List<SegariRouteOrderDTO> orders) {
        return SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(orders.get(i).type());
    }

    private static long[] initiateVehicleArray(long vehicleNumbers, int value) {
        long[] array = new long[(int) vehicleNumbers];
        Arrays.fill(array, value);
        return array;
    }

    private static long[] initiateDemandArray(int length, int start) {
        long[] array = new long[length];
        for (int i = start; i < array.length; i++) {
            array[i] = 1;
        }
        return array;
    }

    private static int[] arrayOf(int length, int value) {
        int[] array = new int[length];
        Arrays.fill(array, value);
        return array;
    }

    private static RoutingIndexManager getRoutingIndexManager(long[][] distanceMatrix, int vehicleNumbers, int[] start, int[] finish) {
        if (distanceMatrix.length == 0) throw SegariRoutingErrors.invalidRoutingParameter("distanceMatrix in getRoutingIndexManager");
        if (vehicleNumbers <= 0) throw SegariRoutingErrors.invalidRoutingParameter("vehicleNumbers in getRoutingIndexManager");
        if (start.length == 0) throw SegariRoutingErrors.invalidRoutingParameter("start in getRoutingIndexManager");
        if (finish.length == 0) throw SegariRoutingErrors.invalidRoutingParameter("finish in getRoutingIndexManager");
        return new RoutingIndexManager(distanceMatrix.length, vehicleNumbers, start, finish);
    }

    private static void addDistanceDimension(RoutingModel routing, RoutingIndexManager manager, int maxTotalDistanceInMeter, long[][] distanceMatrix) {
        if (maxTotalDistanceInMeter <= 0) throw SegariRoutingErrors.invalidRoutingParameter("maxTotalDistanceInMeter in addDistanceDimension");
        if (distanceMatrix.length == 0) throw SegariRoutingErrors.invalidRoutingParameter("distanceMatrix in addDistanceDimension");
        final int transitCallbackIndex =
                routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                    int fromNode = manager.indexToNode(fromIndex);
                    int toNode = manager.indexToNode(toIndex);
                    return distanceMatrix[fromNode][toNode];
                });
        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);
        routing.addDimension(transitCallbackIndex, 0, maxTotalDistanceInMeter,
                true,
                "Distance");
    }

    private static void addMaxOrderCountDimension(RoutingModel routing, RoutingIndexManager manager, long[] maxOrderDemands, long[] maxOrderVehicleCapacities, long[][] distanceMatrix, int vehicleNumbers) {
        if (notEqualToDistanceMatrixLength(maxOrderDemands.length, distanceMatrix)) throw SegariRoutingErrors.invalidRoutingParameter("maxOrderDemands in addMaxOrderCountDimension");
        if (notEqualToVehicleNumber(maxOrderVehicleCapacities.length, vehicleNumbers)) throw SegariRoutingErrors.invalidRoutingParameter("maxOrderVehicleCapacities in addMaxOrderCountDimension");
        final int maxOrderCountCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            return maxOrderDemands[fromNode];
        });
        routing.addDimensionWithVehicleCapacity(maxOrderCountCallbackIndex, 0,
                maxOrderVehicleCapacities,
                true,
                "MaxOrderCount");
    }

    private static boolean notEqualToDistanceMatrixLength(int length, long[][] distanceMatrix) {
        return length != distanceMatrix.length;
    }

    private static boolean notEqualToVehicleNumber(int length, int vehicleNumbers) {
        return length != vehicleNumbers;
    }

    private static void addTimeWindowDimension(RoutingModel routing, RoutingIndexManager manager, long[][] durationMatrix, long[][] timeWindows, int startOrderIndex) {
        final int transitCallbackIndex =
                routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                    int fromNode = manager.indexToNode(fromIndex);
                    int toNode = manager.indexToNode(toIndex);
                    return durationMatrix[fromNode][toNode];
                });
        routing.addDimension(transitCallbackIndex, 120, 86400,
                false,
                "Time");

        RoutingDimension timeDimension = routing.getMutableDimension("Time");

        for (int i = startOrderIndex; i < durationMatrix.length; i++) {
            long index = manager.nodeToIndex(i);
            timeDimension.cumulVar(index).setRange(
                    timeWindows[i][0],
                    timeWindows[i][1]
            );
        }
    }

    private static void addDynamicMaxOrderDimension(RoutingModel routing, RoutingIndexManager manager, List<SegariRouteOrderDTO> orders, int startOrderIndex, int maxOrderCount, int maxNonExtensionCount, int finishIndex) {
        Set<Integer> extensionOrderIndices = getExtensionOrderIndices(orders, startOrderIndex);

        // Extension count
        int extensionCallback = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            if (fromNode == 0) return 0; // dummy
            if (fromNode == 1) return 0; // sp
            return extensionOrderIndices.contains(fromNode) ? 1 : 0;
        });
        routing.addDimension(extensionCallback, 0, maxOrderCount, true, "ExtensionCount");

        // Non-extension count
        int nonExtensionCallback = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            if (fromNode == 0) return 0; // dummy
            if (fromNode == 1) return 0; // sp
            return extensionOrderIndices.contains(fromNode) ? 0 : 1;
        });
        routing.addDimension(nonExtensionCallback, 0, maxOrderCount, true, "NonExtensionCount");

        // Constraint
        Solver solver = routing.solver();
        IntVar extEnd = routing.getMutableDimension("ExtensionCount").cumulVar(routing.end(finishIndex));
        IntVar nonExtEnd = routing.getMutableDimension("NonExtensionCount").cumulVar(routing.end(finishIndex));

        solver.addConstraint(solver.makeLessOrEqual(nonExtEnd, solver.makeSum(extEnd, maxNonExtensionCount)));
    }

    private static Set<Integer> getExtensionOrderIndices(List<SegariRouteOrderDTO> orders, int startOrderIndex) {
        final Set<Integer> extensionOrderIndices = new HashSet<>();
        for (int i = startOrderIndex; i < orders.size(); i++) {
            SegariRouteOrderDTO order = orders.get(i);
            if (order.isExtension()) extensionOrderIndices.add(i);
        }
        return extensionOrderIndices;
    }

    private static void addPenaltyAndDropVisit(RoutingModel routing, RoutingIndexManager manager, int startOrderIndex, Set<Long> mandatoryOrderIds, List<SegariRouteOrderDTO> orders) {
        long penalty = 100_000;
        long mandatoryPenalty = 1_000_000_000;
        for (int i = startOrderIndex; i < orders.size(); ++i) {
            SegariRouteOrderDTO order = orders.get(i);
            routing.addDisjunction(new long[] {manager.nodeToIndex(i)}, mandatoryOrderIds.contains(order.id()) ? mandatoryPenalty : penalty);
        }
    }

    private static Assignment findSolution(RoutingModel routing) {
        RoutingSearchParameters searchParameters =
                main.defaultRoutingSearchParameters()
                        .toBuilder()
                        .setFirstSolutionStrategy(FirstSolutionStrategy.Value.CHRISTOFIDES)
                        .setTimeLimit(Duration.newBuilder().setSeconds(60).build())
                        .setLogSearch(true)
                        .build();
        return routing.solveWithParameters(searchParameters);
    }

    private static List<ArrayList<Long>> getResult(RoutingModel routing, RoutingIndexManager manager,
                                                   Assignment solution, int vehicleNumbers, List<SegariRouteOrderDTO> orders, Set<Long> mandatoryOrderIds) {
        if (Objects.isNull(solution)) return Collections.emptyList();
        List<ArrayList<Long>> results = new ArrayList<>();
        for (int i = 0; i < vehicleNumbers; i++) {
            long index = routing.start(i);

            ArrayList<Long> route = new ArrayList<>();
            while (!routing.isEnd(index)){
                final long thisRoute = manager.indexToNode(index);
                final SegariRouteOrderDTO order = orders.get((int) thisRoute);
                final long orderId = order.id();
                if (orderId != -1L && orderId != -2L) route.add(orderId);
                index = solution.value(routing.nextVar(index));
            }
            results.add(route);
        }

        // Verify all mandatory orders were visited
        if (!mandatoryOrderIds.isEmpty() && !results.isEmpty()) {
            if (!new HashSet<>(results.getFirst()).containsAll(mandatoryOrderIds)) return Collections.emptyList();
        }

        return results;
    }

    static {
        Loader.loadNativeLibraries();
    }
}
