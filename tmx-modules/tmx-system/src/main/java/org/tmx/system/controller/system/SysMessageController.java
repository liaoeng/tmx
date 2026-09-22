package org.tmx.system.controller.system;

import lombok.RequiredArgsConstructor;
import org.tmx.common.core.domain.R;
import org.tmx.common.satoken.utils.LoginHelper;
import org.tmx.common.web.core.BaseController;
import org.tmx.system.domain.vo.SysMessageBoxVo;
import org.tmx.system.service.ISysMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息记录控制器
 *
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/message")
public class SysMessageController extends BaseController {

    private final ISysMessageService messageService;

    /**
     * 查询当前用户消息盒子数据
     *
     * @return 消息盒子数据
     */
    @GetMapping("/box")
    public R<SysMessageBoxVo> getBox() {
        return R.ok(messageService.queryMessageBox(LoginHelper.getUserId()));
    }
}
