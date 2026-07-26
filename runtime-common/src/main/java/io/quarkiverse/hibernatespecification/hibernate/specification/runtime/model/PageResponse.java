package io.quarkiverse.hibernatespecification.hibernate.specification.runtime.model;

import java.util.List;

public record PageResponse<T>(List<T> content, long totalElements, int page, int size, int totalPages) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
    }

    public static <T> PageResponse<T> of(List<T> content, long totalElements, PageRequest pageRequest) {
        int size = pageRequest.size();
        int page = pageRequest.page();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / (double) size);
        return new PageResponse<>(content, totalElements, page, size, totalPages);
    }

    public boolean hasNext() {
        return page + 1 < totalPages;
    }

    public boolean hasPrevious() {
        return page > 0;
    }
}
