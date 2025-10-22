package id.segari.ortools.dto;

public record ResponseDTO<T>(
        T data,
        String errors
) {
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private T data;
        private String errors;

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> errors(String errors) {
            this.errors = errors;
            return this;
        }

        public ResponseDTO<T> build() {
            return new ResponseDTO<>(data, errors);
        }
    }
}
