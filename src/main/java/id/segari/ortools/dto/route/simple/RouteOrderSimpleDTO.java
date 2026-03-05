package id.segari.ortools.dto.route.simple;

import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO;

public record RouteOrderSimpleDTO(
        Long id,
        SegariRouteOrderDTO.SegariRouteOrderEnum type,
        Double latitude,
        Double longitude
) {
}
