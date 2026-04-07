package id.segari.ortools.ortool;

import com.google.ortools.constraintsolver.*;
import id.segari.ortools.dto.route.simple.RouteOrderSimpleDTO;
import id.segari.ortools.dto.route.simple.RouteSimpleDTO;
import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO;
import id.segari.ortools.dto.route.v2.TspResultDTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.external.LatLong;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.external.OSRMTableResponseDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TspWithSpStartAndArbitraryFinishSimple extends BaseTspWithSpStartAndArbitraryFinish {

    public static TspResultDTO run(RouteSimpleDTO dto, OSRMRestService osrmRestService) {
        validateInput(dto);

        final List<RouteOrderSimpleDTO> orders = dto.orders();

        final List<LatLong> latLongs = orders.stream()
                .map(order -> new LatLong(order.latitude(), order.longitude()))
                .toList();

        final OSRMTableResponseDTO tableMatrix = osrmRestService.getMatrix(latLongs);
        final long[][] distanceMatrix = tableMatrix.distances();

        zeroDummyNodeDistances(distanceMatrix);

        final RoutingIndexManager manager = createRoutingManager(distanceMatrix);
        final RoutingModel routing = new RoutingModel(manager);

        int callback = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return distanceMatrix[fromNode][toNode];
        });
        routing.setArcCostEvaluatorOfAllVehicles(callback);

        Assignment solution = findSolution(routing, FirstSolutionStrategy.Value.CHRISTOFIDES);
        return new TspResultDTO(extractSimpleResult(routing, manager, solution, orders));
    }

    private static void validateInput(RouteSimpleDTO dto) {
        if (dto.orders().size() <= ORDER_START_INDEX) throw SegariRoutingErrors.emptyOrder();
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.DUMMY.equals(dto.orders().get(DUMMY_INDEX).type())) {
            throw SegariRoutingErrors.indexZeroNotDummy();
        }
        if (!SegariRouteOrderDTO.SegariRouteOrderEnum.SP.equals(dto.orders().get(SP_INDEX).type())) {
            throw SegariRoutingErrors.indexOneNotSp();
        }
    }

    private static void zeroDummyNodeDistances(long[][] distanceMatrix) {
        for (int i = 0; i < distanceMatrix.length; i++) {
            distanceMatrix[DUMMY_INDEX][i] = 0;
            distanceMatrix[i][DUMMY_INDEX] = 0;
        }
    }

    private static List<Long> extractSimpleResult(RoutingModel routing, RoutingIndexManager manager,
                                                   Assignment solution, List<RouteOrderSimpleDTO> orders) {
        if (Objects.isNull(solution)) return Collections.emptyList();

        List<Long> route = new ArrayList<>();
        long index = routing.start(0);

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
}
