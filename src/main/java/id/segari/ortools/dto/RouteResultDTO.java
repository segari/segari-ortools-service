package id.segari.ortools.dto;

import java.util.ArrayList;
import java.util.List;

public record RouteResultDTO(
        List<ArrayList<Long>> result
) {}
