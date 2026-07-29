package id.segari.ortools.dto.route;

import id.segari.ortools.dto.route.v2.RouteOrderV2DTO;

import java.util.List;

/**
 * The request fields the OSRM matrix preprocessing needs, shared by every routing problem that
 * starts at the SP and finishes anywhere.
 * <p>
 * Both {@code RouteV3DTO} and {@code RouteV4DTO} already declare these as record components, so
 * implementing this interface costs them nothing and lets the preprocessing live in one place
 * instead of being copied per DTO type.
 */
public interface RoutePreprocessingParams {

    Integer maxTotalDistanceWithNonExtensionInMeter();

    Integer maxTotalDistanceWithExtensionInMeter();

    Integer maxDistanceBetweenOrderToNonExtensionInMeter();

    Integer maxDistanceBetweenOrderToExtensionInMeter();

    Integer overheadTimeInSecond();

    Integer slackTimeInSecond();

    Double scaleFactor();

    List<RouteOrderV2DTO> orders();
}
