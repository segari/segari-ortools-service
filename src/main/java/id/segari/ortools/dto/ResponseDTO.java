package id.segari.ortools.dto;

public record ResponseDTO<T>(
        T data,
        String errors
) {}
