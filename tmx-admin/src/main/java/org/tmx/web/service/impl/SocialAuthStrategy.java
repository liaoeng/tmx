package org.tmx.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import org.tmx.common.core.constant.SystemConstants;
import org.tmx.common.core.exception.ServiceException;
import org.tmx.common.core.exception.user.UserException;
import org.tmx.common.core.utils.ValidatorUtils;
import org.tmx.common.json.utils.JsonUtils;
import org.tmx.common.satoken.utils.LoginHelper;
import org.tmx.common.social.config.properties.SocialProperties;
import org.tmx.common.social.utils.SocialUtils;
import org.tmx.system.api.model.LoginUser;
import org.tmx.system.api.model.SocialLoginBody;
import org.tmx.system.domain.vo.SysClientVo;
import org.tmx.system.domain.vo.SysSocialVo;
import org.tmx.system.domain.vo.SysUserVo;
import org.tmx.system.mapper.SysUserMapper;
import org.tmx.system.service.ISysSocialService;
import org.tmx.web.domain.vo.LoginVo;
import org.tmx.web.service.IAuthStrategy;
import org.tmx.web.service.SysLoginService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 第三方授权策略
 *
 * @author thiszhc is 三三
 */
@Slf4j
@Service("social" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class SocialAuthStrategy implements IAuthStrategy {

    private final SocialProperties socialProperties;
    private final ISysSocialService sysSocialService;
    private final SysUserMapper userMapper;
    private final SysLoginService loginService;

    /**
     * 执行第三方授权登录，并校验授权账号与系统账号的绑定关系。
     *
     * @param body   登录信息
     * @param client 客户端信息
     * @return 登录结果
     */
    @Override
    public LoginVo login(String body, SysClientVo client) {
        SocialLoginBody loginBody = JsonUtils.parseObject(body, SocialLoginBody.class);
        ValidatorUtils.validate(loginBody);
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(
            loginBody.getSource(), loginBody.getSocialCode(),
            loginBody.getSocialState(), socialProperties);
        if (!response.ok()) {
            throw new ServiceException(response.getMsg());
        }
        AuthUser authUserData = response.getData();

        List<SysSocialVo> list = sysSocialService.selectByAuthId(authUserData.getSource() + authUserData.getUuid());
        if (CollUtil.isEmpty(list)) {
            throw new ServiceException("你还没有绑定第三方账号，绑定后才可以登录！");
        }
        SysUserVo user = loadUser(list.getFirst().getUserId());
        // 此处可根据登录用户的数据不同 自行创建 loginUser 属性不够用继承扩展就行了
        LoginUser loginUser = loginService.buildLoginUser(user);
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        SaLoginParameter model = IAuthStrategy.buildLoginParameter(client);
        // 生成token
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        return loginVo;
    }

    /**
     * 根据用户ID加载用户，并校验账号状态是否允许登录。
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    private SysUserVo loadUser(Long userId) {
        SysUserVo user = userMapper.selectVoById(userId);
        if (ObjectUtil.isNull(user)) {
            log.info("登录用户：{} 不存在.", "");
            throw new UserException("user.not.exists", "");
        } else if (SystemConstants.DISABLE.equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", "");
            throw new UserException("user.blocked", "");
        }
        return user;
    }

}
