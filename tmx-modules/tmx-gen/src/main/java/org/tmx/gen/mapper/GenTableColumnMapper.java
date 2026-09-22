package org.tmx.gen.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.tmx.common.mybatis.core.mapper.BaseMapperPlus;
import org.tmx.gen.domain.GenTableColumn;

/**
 * 业务字段 数据层
 *
 */
@InterceptorIgnore(dataPermission = "true", tenantLine = "true")
public interface GenTableColumnMapper extends BaseMapperPlus<GenTableColumn, GenTableColumn> {

}
