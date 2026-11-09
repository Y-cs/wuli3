package com.kjs.wuli3.aliyun.sts;

/**
 * 默认 STS 会话策略：按配置的 bucket 和 folder 生成对象上传权限。
 *
 * 注意：bucket 为空时返回空字符串，临时凭据继承角色自身权限。
 *
 * @author lipt create on 2026/11/9 12:00
 */
public final class DefaultStsPolicyProvider implements StsPolicyProvider {

    @Override
    public String resolve(final StsConfig config) {
        if (config.getBucket().isBlank()) {
            return "";
        }
        final String folder = config.getFolder().replaceAll("^/+|/+$", "");
        final String resource = folder.isBlank()
                ? "acs:oss:*:*:" + config.getBucket() + "/*"
                : "acs:oss:*:*:" + config.getBucket() + "/" + folder + "/*";
        return """
                {
                  "Version": "1",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Action": ["oss:Put*", "oss:PutObject"],
                      "Resource": ["${resource}"]
                    }
                  ]
                }
                """.replace("${resource}", resource);
    }
}
