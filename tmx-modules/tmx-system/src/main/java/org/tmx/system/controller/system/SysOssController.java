package org.tmx.system.controller.system;


import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.tmx.common.core.domain.PageResult;
import org.tmx.common.core.domain.R;
import org.tmx.common.core.validate.QueryGroup;
import org.tmx.common.json.utils.JsonUtils;
import org.tmx.common.log.annotation.Log;
import org.tmx.common.log.enums.BusinessType;
import org.tmx.common.mybatis.core.page.PageQuery;
import org.tmx.common.redis.annotation.RepeatSubmit;
import org.tmx.common.web.core.BaseController;
import org.tmx.system.domain.SysOssExt;
import org.tmx.system.domain.bo.SysConfigBo;
import org.tmx.system.domain.bo.SysOssBo;
import org.tmx.system.domain.vo.SysOssVo;
import org.tmx.system.service.ISysConfigService;
import org.tmx.system.service.ISysOssService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传 控制层
 *
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/oss")
public class SysOssController extends BaseController {

    private static final String PREVIEW_CONFIG_KEY = "sys.oss.previewListResource";

    private final ISysOssService ossService;
    private final ISysConfigService configService;

    /**
     * 查询文件列表预览开关。
     *
     * @return 是否允许预览
     */
    @SaCheckPermission("system:oss:list")
    @GetMapping("/preview")
    public R<Boolean> getPreviewEnabled() {
        String value = configService.selectConfigByKey(PREVIEW_CONFIG_KEY);
        return R.ok("true".equals(value));
    }

    /**
     * 修改文件列表预览开关，不开放其他系统参数的修改权限。
     *
     * @param setting 预览设置
     * @return 操作结果
     */
    @SaCheckPermission("system:oss:edit")
    @Log(title = "文件预览设置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/preview")
    public R<Void> updatePreviewEnabled(@Validated @RequestBody PreviewSetting setting) {
        SysConfigBo config = new SysConfigBo();
        config.setConfigKey(PREVIEW_CONFIG_KEY);
        config.setConfigValue(setting.enabled().toString());
        configService.updateConfig(config);
        return R.ok();
    }


    /**
     * 分页查询 OSS 对象存储列表。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return OSS 分页结果
     */
    @SaCheckPermission("system:oss:list")
    @GetMapping("/list")
    public R<PageResult<SysOssVo>> list(@Validated(QueryGroup.class) SysOssBo bo, PageQuery pageQuery) {
        return R.ok(ossService.queryPageList(bo, pageQuery));
    }

    /**
     * 查询OSS对象基于id串
     *
     * @param ossIds OSS对象ID串
     * @return OSS 对象列表
     */
    @SaCheckPermission("system:oss:query")
    @GetMapping("/listByIds/{ossIds}")
    public R<List<SysOssVo>> listByIds(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ossIds) {
        List<SysOssVo> list = ossService.listByIds(Arrays.asList(ossIds));
        return R.ok(list);
    }

    /**
     * 上传OSS对象存储
     *
     * @param file 文件
     * @return 上传结果
     */
    @SaCheckPermission("system:oss:upload")
    @Log(title = "OSS对象存储", businessType = BusinessType.INSERT)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SysOssUploadVo> upload(@RequestPart("file") MultipartFile file, @RequestParam(value = "ossExt", required = false) String ossExtJson) {
        SysOssVo oss = ossService.upload(file, JsonUtils.parseObject(ossExtJson, SysOssExt.class));
        SysOssUploadVo uploadVo = new SysOssUploadVo(oss.getUrl(), oss.getOriginalName(), oss.getOssId().toString());
        return R.ok(uploadVo);
    }

    /**
     * 下载OSS对象
     *
     * @param ossId OSS对象ID
     * @throws IOException IO 异常
     */
    @SaCheckPermission("system:oss:download")
    @GetMapping("/download/{ossId}")
    public ResponseEntity<byte[]> download(@PathVariable Long ossId) throws IOException {
        return ossService.download(ossId);
    }

    /**
     * 删除OSS对象存储
     *
     * @param ossIds OSS对象ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:oss:remove")
    @Log(title = "OSS对象存储", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ossIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ossIds) {
        return toAjax(ossService.deleteWithValidByIds(List.of(ossIds), true));
    }

    /**
     * OSS 上传响应对象。
     *
     * @param url      文件访问地址
     * @param fileName 原始文件名
     * @param ossId    OSS 对象 ID
     */
    public record PreviewSetting(@NotNull Boolean enabled) {
    }

    public record SysOssUploadVo(String url, String fileName, String ossId) {
    }
}
