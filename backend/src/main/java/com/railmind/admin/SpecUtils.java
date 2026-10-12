package com.railmind.admin;

import org.springframework.data.jpa.domain.Specification;

/**
 * Small helper so every admin list endpoint (stations, trains, users,
 * bookings, audit logs, ...) builds its search/filter Specifications the
 * same way instead of each hand-rolling predicate boilerplate.
 */
public class SpecUtils {

    private SpecUtils() {}

    public static <T> Specification<T> likeIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) return null;
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%");
    }

    public static <T> Specification<T> equalsValue(String field, Object value) {
        if (value == null) return null;
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    @SafeVarargs
    public static <T> Specification<T> and(Specification<T>... specs) {
        Specification<T> result = Specification.where(null);
        for (Specification<T> spec : specs) {
            if (spec != null) result = result.and(spec);
        }
        return result;
    }
}
