package id.segari.ortools.dto.route.v3;

import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;

import java.util.List;
import java.util.Set;

public record RouteV3DTO(
        Integer maxTotalDistanceWithNonExtensionInMeter,
        Integer maxTotalDistanceWithExtensionInMeter,
        Integer maxDistanceBetweenOrderToNonExtensionInMeter,
        Integer maxDistanceBetweenOrderToExtensionInMeter,
        Integer maxOrderCountWithExtension,
        Integer maxOrderCountWithNonExtension,
        Set<Long> mandatoryOrders,
        Double scaleFactor,
        Integer overheadTimeInSecond,
        Integer slackTimeInSecond,
        List<RouteOrderV2DTO> orders
) {
}
