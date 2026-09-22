package org.tmx.demo.mapper;

import org.tmx.common.mybatis.annotation.DataColumn;
import org.tmx.common.mybatis.annotation.DataPermission;
import org.tmx.common.mybatis.core.mapper.BaseMapperPlus;
import org.tmx.demo.domain.TestTree;
import org.tmx.demo.domain.vo.TestTreeVo;

/**
 * 测试树表Mapper接口
 *
 * @date 2021-07-26
 */
@DataPermission({
    @DataColumn(key = "deptName", value = "dept_id"),
    @DataColumn(key = "userName", value = "user_id")
})
public interface TestTreeMapper extends BaseMapperPlus<TestTree, TestTreeVo> {

}
