package id.segari.ortools.service.impl;

import id.segari.ortools.dto.RouteDTO;
import id.segari.ortools.dto.RouteResultDTO;
import id.segari.ortools.ortool.SegariRouting;
import id.segari.ortools.service.RouteService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

@Service
@Primary
public class RouteServiceImpl implements RouteService {

    @Override
    public RouteResultDTO vrpWithSpStartAndArbitraryFinish(RouteDTO dto) {
        SegariRouting segariRouting = SegariRouting.newVrpStartFromSpAndArbitraryFinish(dto.getRoute())
                .addDistanceBetweenOrderDimension(dto.getMaxDistanceBetweenOrder())
                .addDistanceWithSpDimension(dto.getMaxDistanceFromSp())
                .addMaxInstanOrderCountDimension(dto.getMaxInstanOrderCount())
                .addMaxTurboOrderCountDimension(dto.getMaxTurboOrderCount());
        if (dto.isUsingRatioDimension()) segariRouting.addExtensionTurboInstanRatioDimension(1, 10);
        if (Objects.nonNull(dto.getAlterVehicleNumberValue())) segariRouting.alterVehicleNumbers(dto.getAlterVehicleNumberValue());
        return RouteResultDTO.builder()
                .result(segariRouting.route())
                .build();
    }

    @Override
    public RouteResultDTO vrpWithArbitraryStartAndArbitraryFinish(RouteDTO dto) {
        return RouteResultDTO.builder()
                .result(SegariRouting.newVrpWithArbitraryStartAndFinish(dto.getRoute())
                        .addDistanceBetweenNodeDimension(dto.getMaxDistanceBetweenOrder())
                        .setResultMustAtMaxOrderCount()
                        .route())
                .build();
    }

    @Override
    public RouteResultDTO tspWithFixStartAndArbitraryFinish(RouteDTO dto, Integer index) {
        return RouteResultDTO.builder()
                .result(SegariRouting.newTspWithStartAndFinish(dto.getRoute(), index)
                        .addDistanceBetweenOrderDimension(dto.getMaxDistanceBetweenOrder())
                        .addDistanceWithSpDimension(dto.getMaxDistanceFromSp())
                        .addMaxInstanOrderCountDimension(dto.getMaxInstanOrderCount())
                        .addMaxTurboOrderCountDimension(dto.getMaxTurboOrderCount())
                        .route())
                .build();
    }
}
