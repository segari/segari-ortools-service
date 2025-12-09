package id.segari.ortools.dto.route.v1;

import id.segari.ortools.validation.group.TspFixStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpArbitraryStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpSpStartArbitraryFinish;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RouteDTO(
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        @Valid
        SegariRouteDTO route,
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class})
        @Min(value = 1, groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Integer maxDistanceBetweenOrder,
        Integer maxDistanceBetweenOrderToExtension,
        @NotNull(groups = VrpSpStartArbitraryFinish.class)
        @Min(value = 1, groups = {VrpSpStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Integer maxDistanceFromSp,
        @NotNull(groups = VrpSpStartArbitraryFinish.class)
        @Min(value = 1, groups = {VrpSpStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Integer maxTurboOrderCount,
        @NotNull(groups = VrpSpStartArbitraryFinish.class)
        @Min(value = 1, groups = {VrpSpStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Integer maxInstanOrderCount,
        @Min(value = 1, groups = VrpSpStartArbitraryFinish.class)
        Integer alterVehicleNumberValue,
        Boolean isUsingRatioDimension,
        Integer extensionCount
) {
    @Override
    public String toString() {
        return "{" +
                "route:" + route.toString() +
                ", maxDistanceBetweenOrder:" + maxDistanceBetweenOrder +
                ", maxDistanceFromSp:" + maxDistanceFromSp +
                ", maxTurboOrderCount:" + maxTurboOrderCount +
                ", maxInstanOrderCount:" + maxInstanOrderCount +
                ", alterVehicleNumberValue:" + alterVehicleNumberValue +
                ", isUsingRatioDimension:" + isUsingRatioDimension +
                ", extensionCount:" + extensionCount +
                '}';
    }
}
