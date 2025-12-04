package id.segari.ortools.dto.route.v2;

import java.util.List;
import java.util.Set;

public record RouteV2DTO(
        Integer maxTotalDistanceWithNonExtensionInMeter,
        Integer maxTotalDistanceWithExtensionInMeter,
        Integer maxDistanceBetweenOrderToNonExtensionInMeter,
        Integer maxDistanceBetweenOrderToExtensionInMeter,
        Integer maxOrderCountWithExtension,
        Integer maxOrderCountWithNonExtension,
        Set<Long> mandatoryOrders,
        boolean useTimeWindow,
        List<RouteOrderV2DTO> orders
) {
}
