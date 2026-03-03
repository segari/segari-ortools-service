package id.segari.ortools.dto.route.v4;

import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;

import java.util.List;
import java.util.Set;

public record RouteV4DTO(
        Integer maxTotalDistanceWithNonExtensionInMeter,
        Integer maxTotalDistanceWithExtensionInMeter,
        Integer maxDistanceBetweenOrderToNonExtensionInMeter,
        Integer maxDistanceBetweenOrderToExtensionInMeter,
        Integer maxOrderCountWithExtension,
        Integer maxOrderCountWithNonExtension,
        Double scaleFactor,
        Integer overheadTimeInSecond,
        Integer slackTimeInSecond,
        Integer vehicleNumber,
        Integer vehicleWithExtensionNumber,
        List<RouteOrderV2DTO> orders
) {
}
