package id.segari.ortools.service.impl;

import id.segari.ortools.dto.RouteDTO;
import id.segari.ortools.dto.RouteResultDTO;
import id.segari.ortools.error.SegariRoutingErrors;
import id.segari.ortools.ortool.SegariRoute;
import id.segari.ortools.service.RouteService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Primary
public class RouteServiceImpl implements RouteService {

    @Override
    public RouteResultDTO vrpWithSpStartAndArbitraryFinish(RouteDTO dto) {
        SegariRoute segariRoute = SegariRoute.newVrpStartFromSpAndArbitraryFinish(dto.route())
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
        return RouteResultDTO.builder()
                .result(segariRoute.route())
                .build();
    }

    @Override
    public RouteResultDTO vrpWithArbitraryStartAndArbitraryFinish(RouteDTO dto) {
        return RouteResultDTO.builder()
                .result(SegariRoute.newVrpWithArbitraryStartAndFinish(dto.route())
                        .addDistanceBetweenNodeDimension(dto.maxDistanceBetweenOrder())
                        .setResultMinimum(dto.route().maxOrderCount())
                        .route())
                .build();
    }

    @Override
    public RouteResultDTO tspWithFixStartAndArbitraryFinish(RouteDTO dto, Integer index) {
        SegariRoute segariRoute = SegariRoute.newTspWithStartAndFinish(dto.route(), index);

        if (Objects.nonNull(dto.maxDistanceBetweenOrder())) segariRoute.addDistanceBetweenOrderDimension(dto.maxDistanceBetweenOrder());
        if (Objects.nonNull(dto.maxDistanceFromSp())) segariRoute.addDistanceWithSpDimension(dto.maxDistanceFromSp());
        if (Objects.nonNull(dto.maxInstanOrderCount())) segariRoute.addMaxInstanOrderCountDimension(dto.maxInstanOrderCount());
        if (Objects.nonNull(dto.maxTurboOrderCount())) segariRoute.addMaxTurboOrderCountDimension(dto.maxTurboOrderCount());

        return RouteResultDTO.builder()
                .result(segariRoute.route())
                .build();
    }
}
