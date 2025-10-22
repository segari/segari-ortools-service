package id.segari.ortools.dto;

import java.util.ArrayList;
import java.util.List;

public record RouteResultDTO(
        List<ArrayList<Long>> result
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ArrayList<Long>> result;

        public Builder result(List<ArrayList<Long>> result) {
            this.result = result;
            return this;
        }

        public RouteResultDTO build() {
            return new RouteResultDTO(result);
        }
    }
}
