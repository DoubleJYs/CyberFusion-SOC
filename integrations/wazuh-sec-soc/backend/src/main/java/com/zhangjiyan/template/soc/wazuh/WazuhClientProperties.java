package com.zhangjiyan.template.soc.wazuh;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "soc.wazuh")
public record WazuhClientProperties(
        String managerUrl,
        String indexerUrl,
        String username,
        String password,
        String managerUsername,
        String managerPassword,
        String indexerUsername,
        String indexerPassword,
        Boolean tlsVerify
) {
}
