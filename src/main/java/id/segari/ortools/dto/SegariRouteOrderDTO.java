package id.segari.ortools.dto;

import id.segari.ortools.validation.group.TspFixStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpArbitraryStartArbitraryFinish;
import id.segari.ortools.validation.group.VrpSpStartArbitraryFinish;
import jakarta.validation.constraints.NotNull;

public record SegariRouteOrderDTO(
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Long id,
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        SegariRouteOrderEnum type,
        Double latitude,
        Double longitude,
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Boolean isExtension,
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Boolean isTurbo,
        @NotNull(groups = {VrpSpStartArbitraryFinish.class, VrpArbitraryStartArbitraryFinish.class, TspFixStartArbitraryFinish.class})
        Boolean isInstan
) {
    @Override
    public String toString() {
        return "{" +
                "id:" + id +
                ", type:" + type +
                ", latitude:" + latitude +
                ", longitude:" + longitude +
                ", isExtension:" + isExtension +
                ", isTurbo:" + isTurbo +
                ", isInstan:" + isInstan +
                '}';
    }

    public enum SegariRouteOrderEnum {
        DUMMY, // id = -1
        SP, // id = -2
        ORDER
    }
}
