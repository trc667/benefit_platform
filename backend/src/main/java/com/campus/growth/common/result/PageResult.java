package com.campus.growth.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应体。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records == null ? Collections.emptyList() : records);
        r.setTotal(total);
        r.setPage(page);
        r.setSize(size);
        return r;
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(Collections.emptyList(), 0, page, size);
    }
}
