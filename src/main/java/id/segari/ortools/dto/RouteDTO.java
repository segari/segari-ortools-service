package id.segari.ortools.dto;

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SegariRouteDTO route;
        private Integer maxDistanceBetweenOrder;
        private Integer maxDistanceFromSp;
        private Integer maxTurboOrderCount;
        private Integer maxInstanOrderCount;
        private Integer alterVehicleNumberValue;
        private Boolean isUsingRatioDimension;
        private Integer extensionCount;

        public Builder route(SegariRouteDTO route) {
            this.route = route;
            return this;
        }

        public Builder maxDistanceBetweenOrder(Integer maxDistanceBetweenOrder) {
            this.maxDistanceBetweenOrder = maxDistanceBetweenOrder;
            return this;
        }

        public Builder maxDistanceFromSp(Integer maxDistanceFromSp) {
            this.maxDistanceFromSp = maxDistanceFromSp;
            return this;
        }

        public Builder maxTurboOrderCount(Integer maxTurboOrderCount) {
            this.maxTurboOrderCount = maxTurboOrderCount;
            return this;
        }

        public Builder maxInstanOrderCount(Integer maxInstanOrderCount) {
            this.maxInstanOrderCount = maxInstanOrderCount;
            return this;
        }

        public Builder alterVehicleNumberValue(Integer alterVehicleNumberValue) {
            this.alterVehicleNumberValue = alterVehicleNumberValue;
            return this;
        }

        public Builder isUsingRatioDimension(Boolean isUsingRatioDimension) {
            this.isUsingRatioDimension = isUsingRatioDimension;
            return this;
        }

        public Builder extensionCount(Integer extensionCount) {
            this.extensionCount = extensionCount;
            return this;
        }

        public RouteDTO build() {
            return new RouteDTO(route, maxDistanceBetweenOrder, maxDistanceFromSp,
                    maxTurboOrderCount, maxInstanOrderCount, alterVehicleNumberValue,
                    isUsingRatioDimension, extensionCount);
        }
    }
}
