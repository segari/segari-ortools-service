package id.segari.ortools.dto.route.v2;

import id.segari.ortools.dto.route.v1.SegariRouteOrderDTO;

public record RouteOrderV2DTO(
        Long id,
        SegariRouteOrderDTO.SegariRouteOrderEnum type,
        Double latitude,
        Double longitude,
        Boolean isExtension,
        Long maxTimeWindow
) {
}
