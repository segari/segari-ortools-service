package id.segari.ortools.service.impl;

import id.segari.ortools.dto.route.v1.RouteDTO;
import id.segari.ortools.dto.route.v1.RouteResultDTO;
import id.segari.ortools.dto.route.v2.RouteV2DTO;
import id.segari.ortools.dto.route.v2.TspResultDTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.external.OSRMRestService;
import id.segari.ortools.ortool.SegariRoute;
import id.segari.ortools.ortool.TspWithSpStartAndArbitraryFinish;
import id.segari.ortools.service.RouteService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Primary
public class RouteServiceImpl implements RouteService {

    private final OSRMRestService osrmRestService;

    public RouteServiceImpl(OSRMRestService osrmRestService) {
        this.osrmRestService = osrmRestService;
    }

    @Override
    public RouteResultDTO vrpWithSpStartAndArbitraryFinish(RouteDTO dto) {
        SegariRoute segariRoute = SegariRoute.newVrpStartFromSpAndArbitraryFinish(dto.route(), osrmRestService)
                .addDistanceBetweenOrderDimension(dto.maxDistanceBetweenOrder())
                .addDistanceWithSpDimension(dto.maxDistanceFromSp())
                .addMaxInstanOrderCountDimension(dto.maxInstanOrderCount())
                .addMaxTurboOrderCountDimension(dto.maxTurboOrderCount());
        if (Boolean.TRUE.equals(dto.isUsingRatioDimension())) {
            if (Objects.isNull(dto.extensionCount())) throw SegariRoutingErrors.invalidRoutingParameter("getExtensionCount in vrpWithSpStartAndArbitraryFinish");
            segariRoute.addExtensionTurboInstanRatioDimension(1, 100, dto.extensionCount());
            segariRoute.setResultMustContainExtension();
            segariRoute.setResultMinimum(4);
            segariRoute.alterVehicleNumbers(dto.extensionCount());
        }
        return new RouteResultDTO(segariRoute.route());
    }

    @Override
    public RouteResultDTO vrpWithArbitraryStartAndArbitraryFinish(RouteDTO dto) {
        SegariRoute segariRoute = SegariRoute.newVrpWithArbitraryStartAndFinish(dto.route(), osrmRestService);
        return new RouteResultDTO(segariRoute.addDistanceBetweenNodeDimension(dto.maxDistanceBetweenOrder())
                .setResultMinimum(dto.route().maxOrderCount())
                .route());
    }

    @Override
    public RouteResultDTO tspWithFixStartAndArbitraryFinish(RouteDTO dto, Integer index) {
        SegariRoute segariRoute = SegariRoute.newTspWithStartAndFinish(dto.route(), index, osrmRestService);

        if (Objects.nonNull(dto.maxDistanceBetweenOrder())) segariRoute.addDistanceBetweenOrderDimension(dto.maxDistanceBetweenOrder());
        if (Objects.nonNull(dto.maxDistanceFromSp())) segariRoute.addDistanceWithSpDimension(dto.maxDistanceFromSp());
        if (Objects.nonNull(dto.maxInstanOrderCount())) segariRoute.addMaxInstanOrderCountDimension(dto.maxInstanOrderCount());
        if (Objects.nonNull(dto.maxTurboOrderCount())) segariRoute.addMaxTurboOrderCountDimension(dto.maxTurboOrderCount());

        return new RouteResultDTO(segariRoute.route());
    }

    @Override
    public RouteResultDTO tspWithSpStartAndArbitraryFinish(RouteDTO dto) {
        final SegariRoute segariRoute = SegariRoute.newTspWithSpStartAndArbitraryFinish(dto.route(), osrmRestService);
        segariRoute.addDistanceBetweenOrderDimension(dto.maxDistanceBetweenOrder());
        segariRoute.addDistanceWithSpDimension(Integer.MAX_VALUE);

        return new RouteResultDTO(segariRoute.route());
    }

    @Override
    public TspResultDTO tspWithSpStartAndArbitraryFinishV2(RouteV2DTO dto) {
        return TspWithSpStartAndArbitraryFinish.run(dto, osrmRestService);
    }
}
