package id.segari.ortools.dto;

import id.segari.ortools.validation.group.TspFixStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpArbitraryStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpSpStartArbitraryFinish;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SegariRouteDTO(
        @NotEmpty(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        List<@NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class}) SegariRouteOrderDTO> orders,
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        @Min(value = 1, groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Integer maxTotalDistanceInMeter,
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        @Min(value = 1, groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Integer maxOrderCount
) {
    @Override
    public String toString() {
        return "{" +
                "orders:" + orders +
                ", maxTotalDistanceInMeter:" + maxTotalDistanceInMeter +
                ", maxOrderCount:" + maxOrderCount +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<SegariRouteOrderDTO> orders;
        private Integer maxTotalDistanceInMeter;
        private Integer maxOrderCount;

        public Builder orders(List<SegariRouteOrderDTO> orders) {
            this.orders = orders;
            return this;
        }

        public Builder maxTotalDistanceInMeter(Integer maxTotalDistanceInMeter) {
            this.maxTotalDistanceInMeter = maxTotalDistanceInMeter;
            return this;
        }

        public Builder maxOrderCount(Integer maxOrderCount) {
            this.maxOrderCount = maxOrderCount;
            return this;
        }

        public SegariRouteDTO build() {
            return new SegariRouteDTO(orders, maxTotalDistanceInMeter, maxOrderCount);
        }
    }
}
