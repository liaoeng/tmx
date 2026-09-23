package org.tmx.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.request.AuthWechatMiniProgramRequest;
import org.tmx.common.core.constant.SystemConstants;
import org.tmx.common.core.exception.ServiceException;
import org.tmx.common.core.utils.ValidatorUtils;
import org.tmx.common.json.utils.JsonUtils;
import org.tmx.common.satoken.utils.LoginHelper;
import org.tmx.system.api.model.XcxLoginBody;
import org.tmx.system.api.model.XcxLoginUser;
import org.tmx.system.domain.vo.SysClientVo;
import org.tmx.system.domain.vo.SysUserVo;
import org.tmx.web.domain.vo.LoginVo;
import org.tmx.web.service.IAuthStrategy;
import org.tmx.web.service.SysLoginService;
import org.springframework.stereotype.Service;

/**
 * 小程序认证策略
 *
 * @author Michelle.Chung
 */
@Slf4j
@Service("xcx" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class XcxAuthStrategy implements IAuthStrategy {

    private final SysLoginService loginService;

    /**
     * 演示微信小程序登录流程；应用密钥和 openid 用户绑定仍为占位逻辑，不能直接用于实际登录。
     *
     * @param body   登录请求体
     * @param client 当前客户端配置
     * @return 登录结果
     */
    @Override
    public LoginVo login(String body, SysClientVo client) {
        XcxLoginBody loginBody = JsonUtils.parseObject(body, XcxLoginBody.class);
        ValidatorUtils.validate(loginBody);
        // xcxCode 为 小程序调用 wx.login 授权后获取
        String xcxCode = loginBody.getXcxCode();
        // 请求中的 appid 用于选择小程序应用。
        String appid = loginBody.getAppid();

        // 使用小程序凭证换取 openid；clientSecret 当前仍是占位值。
        AuthRequest authRequest = new AuthWechatMiniProgramRequest(AuthConfig.builder()
            .clientId(appid).clientSecret("自行填写密钥 可根据不同appid填入不同密钥")
            .ignoreCheckRedirectUri(true).ignoreCheckState(true).build());
        AuthCallback authCallback = new AuthCallback();
        authCallback.setCode(xcxCode);
        AuthResponse<AuthUser> resp = authRequest.login(authCallback);
        String openid, unionId;
        if (resp.ok()) {
            AuthToken token = resp.getData().getToken();
            openid = token.getOpenId();
            // 微信小程序只有关联到微信开放平台下之后才能获取到 unionId，因此unionId不一定能返回。
            unionId = token.getUnionId();
        } else {
            throw new ServiceException(resp.getMsg());
        }
        // 绑定用户查询尚未实现，启用该登录方式前必须补齐。
        SysUserVo user = loadUserByOpenid(openid);
        // 将查询结果写入小程序登录态；当前结果仍是占位对象。
        XcxLoginUser loginUser = new XcxLoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUserName());
        loginUser.setNickname(user.getNickName());
        loginUser.setUserType(user.getUserType());
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        loginUser.setOpenid(openid);

        SaLoginParameter model = IAuthStrategy.buildLoginParameter(client);
        // 生成token
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        loginVo.setOpenid(openid);
        return loginVo;
    }

    /**
     * 小程序绑定用户查询占位方法：当前未执行真实查询。
     *
     * @param openid 小程序用户唯一标识
     * @return 当前仅返回空的用户占位对象
     */
    private SysUserVo loadUserByOpenid(String openid) {
        // TODO：按 openid 查询绑定用户，按产品规则处理首次登录。
        SysUserVo user = new SysUserVo();
        if (ObjectUtil.isNull(user)) {
            log.info("登录用户：{} 不存在.", openid);
            // TODO：拒绝未绑定用户，或按产品规则完成注册。
        } else if (SystemConstants.DISABLE.equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", openid);
            // TODO：拒绝已停用用户登录。
        }
        return user;
    }

}
