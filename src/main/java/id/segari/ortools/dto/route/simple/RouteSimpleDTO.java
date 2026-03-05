package id.segari.ortools.dto.route.simple;

import java.util.List;

public record RouteSimpleDTO(
        List<RouteOrderSimpleDTO> orders
) {
}
