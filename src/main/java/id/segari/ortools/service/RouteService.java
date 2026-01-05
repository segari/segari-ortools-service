package id.segari.ortools.service;

import id.segari.ortools.dto.route.v1.RouteDTO;
import id.segari.ortools.dto.route.v1.RouteResultDTO;
import id.segari.ortools.dto.route.v2.RouteV2DTO;
import id.segari.ortools.dto.route.v2.TspResultDTO;
import id.segari.ortools.dto.route.v3.RouteV3DTO;
import id.segari.ortools.validation.group.TspFixStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpArbitraryStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpSpStartArbitraryFinish;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@Validated
public interface RouteService {
    @Validated(VrpSpStartArbitraryFinish.class)
    RouteResultDTO vrpWithSpStartAndArbitraryFinish(@Valid RouteDTO dto);
    @Validated(VrpArbitraryStartArbitraryFinish.class)
    RouteResultDTO vrpWithArbitraryStartAndArbitraryFinish(@Valid RouteDTO dto);
    @Validated(TspFixStartArbitraryFinish.class)
    RouteResultDTO tspWithFixStartAndArbitraryFinish(@Valid RouteDTO dto, @NotNull Integer index);
    RouteResultDTO tspWithSpStartAndArbitraryFinish(@Valid RouteDTO dto);
    TspResultDTO tspWithSpStartAndArbitraryFinishV2(@Valid RouteV2DTO dto);
    TspResultDTO tspWithSpStartAndArbitraryFinishV3(@Valid RouteV3DTO dto);
}
