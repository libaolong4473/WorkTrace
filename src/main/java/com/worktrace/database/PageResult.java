package com.worktrace.database;

import java.util.Collections;
import java.util.List;

/**
 * 分页查询结果封装。
 *
 * @param <T> 实体类型
 */
public class PageResult<T> {

    private final List<T> data;
    private final long total;
    private final int page;
    private final int pageSize;

    public PageResult(List<T> data, long total, int page, int pageSize) {
        this.data     = data != null ? data : Collections.emptyList();
        this.total    = total;
        this.page     = page;
        this.pageSize = pageSize;
    }

    /** 空结果工厂方法。 */
    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
    }

    /** 当前页数据列表。 */
    public List<T> getData()      { return data; }

    /** 符合条件的总记录数。 */
    public long getTotal()        { return total; }

    /** 当前页码(从 1 开始)。 */
    public int getPage()          { return page; }

    /** 每页条数。 */
    public int getPageSize()      { return pageSize; }

    /** 总页数。 */
    public int getTotalPages()    {
        return pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }

    /** 是否有下一页。 */
    public boolean hasNext()      { return page < getTotalPages(); }

    /** 是否有上一页。 */
    public boolean hasPrevious()  { return page > 1; }
}
