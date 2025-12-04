package id.segari.ortools.dto.route.v1;

import java.util.ArrayList;
import java.util.List;

public record RouteResultDTO(
        List<ArrayList<Long>> result
) {}
