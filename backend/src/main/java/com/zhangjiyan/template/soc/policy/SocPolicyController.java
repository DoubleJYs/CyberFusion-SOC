package com.zhangjiyan.template.soc.policy;

import com.zhangjiyan.template.common.audit.OperationAudit;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.policy.adapter.EventAdapterMappingsRequest;
import com.zhangjiyan.template.soc.policy.adapter.EventAdapterPolicyService;
import com.zhangjiyan.template.soc.policy.adapter.EventAdapterPreviewRequest;
import com.zhangjiyan.template.soc.policy.adapter.EventAdapterProfileRequest;
import com.zhangjiyan.template.soc.policy.adapter.SocEventAdapterProfile;
import com.zhangjiyan.template.soc.playbook.PlaybookRequest;
import com.zhangjiyan.template.soc.playbook.ResponsePlaybookService;
import com.zhangjiyan.template.soc.playbook.SocResponsePlaybook;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/policies")
public class SocPolicyController {

    private final LocalCheckPolicyService service;
    private final EventAdapterPolicyService eventAdapterPolicyService;
    private final ResponsePlaybookService playbookService;

    @GetMapping("/local-check-commands")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:list')")
    public ApiResult<PageResult<SocLocalCheckCommand>> localCheckCommands(@RequestParam(defaultValue = "1") long pageNum,
                                                                          @RequestParam(defaultValue = "10") long pageSize,
                                                                          @RequestParam(required = false) String osType,
                                                                          @RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.page(pageNum, pageSize, osType, status, keyword));
    }

    @PostMapping("/local-check-commands")
    @OperationAudit("SOC_POLICY.CREATE_LOCAL_CHECK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:create')")
    public ApiResult<SocLocalCheckCommand> createLocalCheckCommand(@Valid @RequestBody LocalCheckCommandRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PutMapping("/local-check-commands/{id}")
    @OperationAudit("SOC_POLICY.UPDATE_LOCAL_CHECK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update')")
    public ApiResult<SocLocalCheckCommand> updateLocalCheckCommand(@PathVariable Long id,
                                                                   @Valid @RequestBody LocalCheckCommandRequest request) {
        return ApiResult.ok(service.update(id, request));
    }

    @PostMapping("/local-check-commands/{id}/publish")
    @OperationAudit("SOC_POLICY.PUBLISH_LOCAL_CHECK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:publish')")
    public ApiResult<SocLocalCheckCommand> publishLocalCheckCommand(@PathVariable Long id) {
        return ApiResult.ok(service.publish(id));
    }

    @PostMapping("/local-check-commands/{id}/enabled")
    @OperationAudit("SOC_POLICY.ENABLE_LOCAL_CHECK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:disable')")
    public ApiResult<SocLocalCheckCommand> changeLocalCheckEnabled(@PathVariable Long id,
                                                                   @RequestBody PolicyEnabledRequest request) {
        return ApiResult.ok(service.changeEnabled(id, request.enabled()));
    }

    @PostMapping("/local-check-commands/{id}/disable")
    @OperationAudit("SOC_POLICY.DISABLE_LOCAL_CHECK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:disable')")
    public ApiResult<SocLocalCheckCommand> disableLocalCheckCommand(@PathVariable Long id) {
        return ApiResult.ok(service.disable(id));
    }

    @PostMapping("/local-check-commands/precheck")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update') or hasAuthority('soc:policy:create')")
    public ApiResult<LocalCheckPolicyService.PrecheckResult> precheck(@Valid @RequestBody LocalCheckCommandRequest request) {
        return ApiResult.ok(service.precheck(request));
    }

    @PostMapping("/local-check-commands/{id}/validate")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update') or hasAuthority('soc:policy:create')")
    public ApiResult<LocalCheckPolicyService.PrecheckResult> validateLocalCheckCommand(@PathVariable Long id) {
        return ApiResult.ok(service.validateExisting(id));
    }

    @GetMapping("/local-check-commands/audits")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:audit')")
    public ApiResult<List<SocLocalCheckCommand>> audits() {
        return ApiResult.ok(service.audit());
    }

    @GetMapping("/event-adapters")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:list')")
    public ApiResult<PageResult<SocEventAdapterProfile>> eventAdapters(@RequestParam(name = "pageNum", defaultValue = "1") long pageNum,
                                                                       @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
                                                                       @RequestParam(name = "sourceType", required = false) String sourceType,
                                                                       @RequestParam(name = "status", required = false) String status,
                                                                       @RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResult.ok(eventAdapterPolicyService.page(pageNum, pageSize, sourceType, status, keyword));
    }

    @GetMapping("/event-adapters/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:list')")
    public ApiResult<SocEventAdapterProfile> eventAdapterDetail(@PathVariable Long id) {
        return ApiResult.ok(eventAdapterPolicyService.detail(id));
    }

    @PostMapping("/event-adapters")
    @OperationAudit("SOC_POLICY.CREATE_EVENT_ADAPTER")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:create')")
    public ApiResult<SocEventAdapterProfile> createEventAdapter(@Valid @RequestBody EventAdapterProfileRequest request) {
        return ApiResult.ok(eventAdapterPolicyService.create(request));
    }

    @PutMapping("/event-adapters/{id}")
    @OperationAudit("SOC_POLICY.UPDATE_EVENT_ADAPTER")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update')")
    public ApiResult<SocEventAdapterProfile> updateEventAdapter(@PathVariable Long id,
                                                                @Valid @RequestBody EventAdapterProfileRequest request) {
        return ApiResult.ok(eventAdapterPolicyService.update(id, request));
    }

    @PostMapping("/event-adapters/{id}/validate")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update') or hasAuthority('soc:policy:create')")
    public ApiResult<EventAdapterPolicyService.AdapterValidationResult> validateEventAdapter(@PathVariable Long id) {
        return ApiResult.ok(eventAdapterPolicyService.validateExisting(id));
    }

    @PostMapping("/event-adapters/{id}/publish")
    @OperationAudit("SOC_POLICY.PUBLISH_EVENT_ADAPTER")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:publish')")
    public ApiResult<SocEventAdapterProfile> publishEventAdapter(@PathVariable Long id) {
        return ApiResult.ok(eventAdapterPolicyService.publish(id));
    }

    @PostMapping("/event-adapters/{id}/disable")
    @OperationAudit("SOC_POLICY.DISABLE_EVENT_ADAPTER")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:disable')")
    public ApiResult<SocEventAdapterProfile> disableEventAdapter(@PathVariable Long id) {
        return ApiResult.ok(eventAdapterPolicyService.disable(id));
    }

    @GetMapping("/event-adapters/{id}/mappings")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:list')")
    public ApiResult<EventAdapterPolicyService.AdapterBundle> eventAdapterMappings(@PathVariable Long id) {
        return ApiResult.ok(eventAdapterPolicyService.mappings(id));
    }

    @PutMapping("/event-adapters/{id}/mappings")
    @OperationAudit("SOC_POLICY.UPDATE_EVENT_ADAPTER_MAPPINGS")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update')")
    public ApiResult<EventAdapterPolicyService.AdapterBundle> updateEventAdapterMappings(@PathVariable Long id,
                                                                                         @Valid @RequestBody EventAdapterMappingsRequest request) {
        return ApiResult.ok(eventAdapterPolicyService.updateMappings(id, request));
    }

    @PostMapping("/event-adapters/{id}/preview")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:list')")
    public ApiResult<EventAdapterPolicyService.AdapterPreviewResult> previewEventAdapter(@PathVariable Long id,
                                                                                        @Valid @RequestBody EventAdapterPreviewRequest request) {
        return ApiResult.ok(eventAdapterPolicyService.preview(id, request));
    }

    @GetMapping("/playbooks")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:list')")
    public ApiResult<PageResult<SocResponsePlaybook>> playbooks(@RequestParam(defaultValue = "1") long pageNum,
                                                                @RequestParam(defaultValue = "10") long pageSize,
                                                                @RequestParam(required = false) String sourceType,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String keyword) {
        return ApiResult.ok(playbookService.page(pageNum, pageSize, sourceType, status, keyword));
    }

    @GetMapping("/playbooks/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:list')")
    public ApiResult<ResponsePlaybookService.PlaybookDetail> playbookDetail(@PathVariable Long id) {
        return ApiResult.ok(playbookService.detail(id));
    }

    @PostMapping("/playbooks")
    @OperationAudit("SOC_POLICY.CREATE_RESPONSE_PLAYBOOK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:create')")
    public ApiResult<ResponsePlaybookService.PlaybookDetail> createPlaybook(@Valid @RequestBody PlaybookRequest request) {
        return ApiResult.ok(playbookService.create(request));
    }

    @PutMapping("/playbooks/{id}")
    @OperationAudit("SOC_POLICY.UPDATE_RESPONSE_PLAYBOOK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update')")
    public ApiResult<ResponsePlaybookService.PlaybookDetail> updatePlaybook(@PathVariable Long id,
                                                                            @Valid @RequestBody PlaybookRequest request) {
        return ApiResult.ok(playbookService.update(id, request));
    }

    @PostMapping("/playbooks/{id}/validate")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:update') or hasAuthority('soc:policy:create')")
    public ApiResult<ResponsePlaybookService.ValidationResult> validatePlaybook(@PathVariable Long id) {
        return ApiResult.ok(playbookService.validateExisting(id));
    }

    @PostMapping("/playbooks/{id}/publish")
    @OperationAudit("SOC_POLICY.PUBLISH_RESPONSE_PLAYBOOK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:publish')")
    public ApiResult<ResponsePlaybookService.PlaybookDetail> publishPlaybook(@PathVariable Long id) {
        return ApiResult.ok(playbookService.publish(id));
    }

    @PostMapping("/playbooks/{id}/disable")
    @OperationAudit("SOC_POLICY.DISABLE_RESPONSE_PLAYBOOK")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:policy:disable')")
    public ApiResult<ResponsePlaybookService.PlaybookDetail> disablePlaybook(@PathVariable Long id) {
        return ApiResult.ok(playbookService.disable(id));
    }

    public record PolicyEnabledRequest(boolean enabled) {
    }
}
