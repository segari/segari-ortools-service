package id.segari.ortools.dto;

import id.segari.ortools.validation.group.TspFixStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpArbitraryStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpSpStartArbitraryFinish;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record SegariRouteDTO(
        @NotEmpty(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        List<@NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class}) SegariRouteOrderDTO> orders,

        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        @Min(value = 1, groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Integer maxTotalDistanceInMeter,

        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        @Min(value = 1, groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Integer maxOrderCount,
        Set<Long> mandatoryOrders
) {
    @Override
    public String toString() {
        return "{" +
                "orders:" + orders +
                ", maxTotalDistanceInMeter:" + maxTotalDistanceInMeter +
                ", maxOrderCount:" + maxOrderCount +
                '}';
    }
}
