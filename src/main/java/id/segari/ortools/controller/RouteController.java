package id.segari.ortools.controller;

import id.segari.ortools.dto.ResponseDTO;
import id.segari.ortools.dto.route.v1.RouteDTO;
import id.segari.ortools.dto.route.v1.RouteResultDTO;
import id.segari.ortools.dto.route.v2.RouteV2DTO;
import id.segari.ortools.dto.route.v2.TspResultDTO;
import id.segari.ortools.dto.route.v3.RouteV3DTO;
import id.segari.ortools.service.RouteService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/vrp/sp-start/arbitrary-finish")
    public ResponseDTO<RouteResultDTO> vrp1(
            @RequestBody RouteDTO request
    ){
        return new ResponseDTO<>(routeService.vrpWithSpStartAndArbitraryFinish(request), null);
    }

    @PostMapping("/vrp/arbitrary-start/arbitrary-finish")
    public ResponseDTO<RouteResultDTO> vrp2(
            @RequestBody RouteDTO request
    ){
        return new ResponseDTO<>(routeService.vrpWithArbitraryStartAndArbitraryFinish(request), null);
    }

    @PostMapping("/tsp/fix-start/{index}/arbitrary-finish")
    public ResponseDTO<RouteResultDTO> tsp1(
            @PathVariable Integer index,
            @RequestBody RouteDTO request
    ){
        return new ResponseDTO<>(routeService.tspWithFixStartAndArbitraryFinish(request, index), null);
    }

    @PostMapping("/tsp/sp-start/arbitrary-finish/use-osrm")
    public ResponseDTO<RouteResultDTO> tsp2(
            @RequestBody RouteDTO request
    ){
        return new ResponseDTO<>(routeService.tspWithSpStartAndArbitraryFinish(request), null);
    }

    @PostMapping("/v2/tsp/sp-start/arbitrary-finish/use-osrm")
    public ResponseDTO<TspResultDTO> tsp3(
            @RequestBody RouteV2DTO request
    ){
        return new ResponseDTO<>(routeService.tspWithSpStartAndArbitraryFinishV2(request), null);
    }

    @PostMapping("/v3/tsp/sp-start/arbitrary-finish/use-osrm")
    public ResponseDTO<TspResultDTO> tsp4(
            @RequestBody RouteV3DTO request
    ){
        return new ResponseDTO<>(routeService.tspWithSpStartAndArbitraryFinishV3(request), null);
    }

}
