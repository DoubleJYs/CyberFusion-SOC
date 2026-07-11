package com.zhangjiyan.template.soc.fim;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.soc.SocSecurityScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FimWatchPathServiceTest {

    @Test
    void pageAcceptsOmittedOptionalFilters() {
        SocFimWatchPathMapper mapper = mock(SocFimWatchPathMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SocFimWatchPath> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        FimWatchPathService service = new FimWatchPathService(mapper, mock(SocSecurityScope.class));

        PageResult<SocFimWatchPath> page = service.page(1, 10, null, null, null, null);

        assertThat(page.records()).isEmpty();
        assertThat(page.total()).isZero();
    }
}
