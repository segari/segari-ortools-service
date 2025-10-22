package id.segari.ortools.controller;

import id.segari.ortools.dto.ResponseDTO;
import id.segari.ortools.dto.RouteDTO;
import id.segari.ortools.dto.RouteResultDTO;
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
        return ResponseDTO.<RouteResultDTO>builder()
                .data(routeService.vrpWithSpStartAndArbitraryFinish(request))
                .build();
    }

    @PostMapping("/vrp/arbitrary-start/arbitrary-finish")
    public ResponseDTO<RouteResultDTO> vrp2(
            @RequestBody RouteDTO request
    ){
        return ResponseDTO.<RouteResultDTO>builder()
                .data(routeService.vrpWithArbitraryStartAndArbitraryFinish(request))
                .build();
    }

    @PostMapping("/tsp/fix-start/{index}/arbitrary-finish")
    public ResponseDTO<RouteResultDTO> tsp1(
            @PathVariable Integer index,
            @RequestBody RouteDTO request
    ){
        return ResponseDTO.<RouteResultDTO>builder()
                .data(routeService.tspWithFixStartAndArbitraryFinish(request, index))
                .build();
    }

}
