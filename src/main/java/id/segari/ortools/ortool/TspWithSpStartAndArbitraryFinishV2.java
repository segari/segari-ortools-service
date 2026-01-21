package id.segari.ortools.ortool;

import com.google.ortools.constraintsolver.*;
import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO;
import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;
import id.segari.ortools.dto.route.v2.TspResultDTO;
import id.segari.ortools.dto.route.v3.RouteV3DTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.external.LatLong;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.external.OSRMTableResponseDTO;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class TspWithSpStartAndArbitraryFinishV2 extends BaseTspWithSpStartAndArbitraryFinish {

    protected static final int GLOBAL_SPAN_COST_COEFFICIENT = 100;

    public static TspResultDTO run(RouteV3DTO dto, OSRMRestService osrmRestService) {
        validateInput(dto);

        final List<RouteOrderV2DTO> orders = dto.orders();
        final Set<Long> mandatoryOrderIds = getMandatoryOrderIds(dto);
        final Set<Integer> extensionOrderIndices = getExtensionOrderIndices(orders);
        final boolean hasExtensions = !extensionOrderIndices.isEmpty();

        final OSRMTableResponseDTO tableMatrix = fetchTableMatrix(dto, osrmRestService);
        final long[][] distanceMatrix = preprocessDistanceMatrix(tableMatrix.distances(), orders, dto, hasExtensions);
        final long[][] timeWindows = initializeTimeWindows(orders);
        final long[][] durationMatrix = preprocessDurationMatrix(tableMatrix.durations(), orders, timeWindows, dto);

        final RoutingIndexManager manager = createRoutingManager(distanceMatrix);
        final RoutingModel routing = new RoutingModel(manager);

        setupDimensions(routing, manager, orders, distanceMatrix, durationMatrix, timeWindows, extensionOrderIndices, dto, hasExtensions);
        addPenaltyAndDropVisit(routing, manager, orders, mandatoryOrderIds);

        Assignment solution = findSolution(routing);
        return new TspResultDTO(extractResult(routing, manager, solution, orders, mandatoryOrderIds));
    }

    // ==================== Validation ====================

    private static void validateInput(RouteV3DTO dto) {
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
    }

    // ==================== Data Preparation ====================

    private static Set<Long> getMandatoryOrderIds(RouteV3DTO dto) {
        return CollectionUtils.isEmpty(dto.mandatoryOrders()) ? Collections.emptySet() : dto.mandatoryOrders();
    }

    private static OSRMTableResponseDTO fetchTableMatrix(RouteV3DTO dto, OSRMRestService osrmRestService) {
        List<LatLong> latLongs = dto.orders().stream()
                .map(order -> new LatLong(order.latitude(), order.longitude()))
                .toList();
        return osrmRestService.getMatrixWithScaleFactor(latLongs, dto.scaleFactor());
    }

    // ==================== Matrix Preprocessing ====================

    private static long[][] preprocessDistanceMatrix(long[][] distanceMatrix, List<RouteOrderV2DTO> orders,
                                                     RouteV3DTO dto, boolean hasExtensions) {
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

    private static long[][] preprocessDurationMatrix(long[][] durationMatrix, List<RouteOrderV2DTO> orders,
                                                     long[][] timeWindows, RouteV3DTO dto) {
        int length = orders.size();
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                if (isDummyNode(i, orders) || isDummyNode(j, orders)) {
                    durationMatrix[i][j] = 0;
                }
                if (isSpNode(i, orders) && !isDummyNode(j, orders)) {
                    durationMatrix[i][j] += dto.overheadTimeInSecond();
                }
                if (isSpNode(i, orders) && durationMatrix[i][j] > timeWindows[j][1]) {
                    timeWindows[j][1] = TIME_WINDOW_BYPASS;
                }
            }
        }
        return durationMatrix;
    }

    // ==================== Dimensions Setup ====================

    private static void setupDimensions(RoutingModel routing, RoutingIndexManager manager,
                                        List<RouteOrderV2DTO> orders, long[][] distanceMatrix,
                                        long[][] durationMatrix, long[][] timeWindows,
                                        Set<Integer> extensionOrderIndices, RouteV3DTO dto, boolean hasExtensions) {
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

        addTimeWindowDimension(routing, manager, durationMatrix, timeWindows, dto);
    }

    private static void addTimeWindowDimension(RoutingModel routing, RoutingIndexManager manager,
                                               long[][] durationMatrix, long[][] timeWindows, RouteV3DTO dto) {
        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return durationMatrix[fromNode][toNode];
        });
        routing.addDimension(callback, dto.slackTimeInSecond(), MAX_ROUTE_TIME, false, "Time");

        RoutingDimension timeDimension = routing.getMutableDimension("Time");
        for (int i = ORDER_START_INDEX; i < durationMatrix.length; i++) {
            long index = manager.nodeToIndex(i);
            timeDimension.cumulVar(index).setRange(timeWindows[i][0], timeWindows[i][1]);
        }

        // Minimize time span to prioritize orders with tighter deadlines first
//        timeDimension.setGlobalSpanCostCoefficient(GLOBAL_SPAN_COST_COEFFICIENT);
    }
}
