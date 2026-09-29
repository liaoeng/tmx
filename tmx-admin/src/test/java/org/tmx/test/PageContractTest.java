package org.tmx.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tmx.common.core.domain.PageResult;
import org.tmx.common.core.domain.R;
import org.tmx.common.core.exception.ServiceException;
import org.tmx.common.mybatis.core.page.PageQuery;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 分页响应及排序输入回归测试，不依赖数据库和 Redis。 */
class PageContractTest {

    /** 前端列表从 data.rows/data.total 读取分页，不能退回旧的顶层结构。 */
    @Test
    @DisplayName("分页响应保持统一的嵌套结构")
    void serializesNestedPageResponse() {
        var mapper = JsonMapper.builder().build();
        var response = mapper.valueToTree(R.ok(PageResult.build(List.of("第一条"), 21)));
        assertEquals(200, response.get("code").asInt());
        assertEquals(21, response.get("data").get("total").asLong());
        assertEquals("第一条", response.get("data").get("rows").get(0).asString());
        assertFalse(response.has("rows"));
        assertFalse(response.has("total"));
    }

    /** 数据源没有结果时返回空数组，避免前端把空值误当成异常结构。 */
    @Test
    @DisplayName("空结果保持空列表和零总数")
    void buildsEmptyPage() {
        var page = PageResult.build(null, 0);
        assertNotNull(page.getRows());
        assertTrue(page.getRows().isEmpty());
        assertEquals(0, page.getTotal());
    }

    /** Element Plus 排序方向和驼峰字段应正确转换为数据库排序参数。 */
    @Test
    @DisplayName("前端多字段排序转换正确")
    void convertsTableSortDirections() {
        var query = new PageQuery(10, 2);
        query.setOrderByColumn("createTime,userId");
        query.setIsAsc("descending,ascending");
        var page = query.build();
        assertEquals(2, page.getCurrent());
        assertEquals(10, page.getSize());
        assertEquals(10, query.getFirstNum());
        assertEquals("create_time", page.orders().get(0).getColumn());
        assertFalse(page.orders().get(0).isAsc());
        assertEquals("user_id", page.orders().get(1).getColumn());
        assertTrue(page.orders().get(1).isAsc());
    }

    /** 非法方向和字段/方向数量不匹配必须拒绝，不能静默执行错误排序。 */
    @Test
    @DisplayName("拒绝非法排序参数")
    void rejectsInvalidSort() {
        var query = new PageQuery(10, 1);
        query.setOrderByColumn("userId");
        query.setIsAsc("desc;drop table sys_user");
        assertThrows(ServiceException.class, query::build);
        query.setIsAsc("asc,desc");
        assertThrows(ServiceException.class, query::build);
    }
}
