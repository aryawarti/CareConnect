package com.careconnect.identity.api.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Standard envelope (docs/api/guidelines.md). Errors use RFC 7807 instead. */
public record ApiEnvelope<T>(T data, PageMeta meta) {

    public record PageMeta(int page, int size, long totalElements, int totalPages) { }

    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>(data, null);
    }

    public static <E, T> ApiEnvelope<List<T>> ofPage(Page<E> page, Function<E, T> mapper) {
        return new ApiEnvelope<>(page.getContent().stream().map(mapper).toList(),
                new PageMeta(page.getNumber(), page.getSize(),
                        page.getTotalElements(), page.getTotalPages()));
    }
}
