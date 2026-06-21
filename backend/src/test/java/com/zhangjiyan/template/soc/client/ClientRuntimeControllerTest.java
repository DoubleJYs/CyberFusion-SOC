package com.zhangjiyan.template.soc.client;

import com.zhangjiyan.template.common.file.FileStorageProperties;
import com.zhangjiyan.template.common.result.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRuntimeControllerTest {

    @Test
    void compatibilityShouldExposeSafeRuntimeStatus() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBaseDir(System.getProperty("user.home") + "/Environment/cyberfusion-platform");
        ClientRuntimeController controller = new ClientRuntimeController(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 Chrome/125.0 Safari/537.36");

        ApiResult<ClientRuntimeController.ClientRuntimeCompatibility> result = controller.compatibility(request);

        assertThat(result.data().platform().browserFamily()).isEqualTo("Chromium");
        assertThat(result.data().dataRoot().environmentRoot()).isTrue();
        assertThat(result.data().dataRoot().displayName()).isEqualTo("cyberfusion-platform");
        assertThat(result.data().capabilities()).extracting(ClientRuntimeController.RuntimeCapability::key)
                .contains("route_context", "local_terminal_guard", "vm_console_embed", "data_root_isolation");
    }

    @Test
    void compatibilityShouldUseEnvironmentDefaultWhenBaseDirIsNotConfigured() {
        FileStorageProperties properties = new FileStorageProperties();
        ClientRuntimeController controller = new ClientRuntimeController(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 Chrome/125.0 Safari/537.36");

        ApiResult<ClientRuntimeController.ClientRuntimeCompatibility> result = controller.compatibility(request);

        assertThat(result.data().dataRoot().environmentRoot()).isTrue();
        assertThat(result.data().dataRoot().displayName()).isEqualTo("cyberfusion-platform");
    }
}
