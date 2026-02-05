package com.dapm.security_service.controllers.PeerApi;

import com.dapm.security_service.models.*;
import com.dapm.security_service.models.dtos.ConfigureValidationDto;
import com.dapm.security_service.models.dtos.ConfirmationResponse;
import com.dapm.security_service.models.dtos.MissingPermissionsDto;
import com.dapm.security_service.models.dtos.peer.RequestResponse;
import com.dapm.security_service.models.dtos2.PipelineProcessingElementRequestOutboundDto;
import com.dapm.security_service.models.enums.AccessRequestStatus;
import com.dapm.security_service.models.enums.PipelinePhase;
import com.dapm.security_service.models.enums.Tier;
import com.dapm.security_service.models.models2.ValidatedPipelineConfig;
import com.dapm.security_service.repositories.*;
import com.dapm.security_service.services.TokenService;
import com.dapm.security_service.services.TokenVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/peer")
public  class PeAccessRequest {

    @Autowired
    private TiersRepository tiersRepository;
    @Autowired
    private SubscriberOrganizationRepository subscriberOrganizationRepository;
    @Autowired
    private ProcessingElementRepository peRepository;
    @Autowired
    private PipelineProcessingElementRequestRepository pipelinePeReqRepo;
    @Autowired
    private TokenVerificationService tokenVerificationService;
    @Autowired
    private TokenService tokenService;
    @Autowired private ValidatePipelineRepository validatePipelineRepository;

    @Autowired private PipelineRepositoryy pipelineRepository;
    @Autowired private PipelinePeInstanceRepo pipelinePeInstanceRepo;
    @PostMapping("/request-access")
    public ResponseEntity<?> handshake(@RequestBody PipelineProcessingElementRequestOutboundDto requestDto) {
        boolean verified= tokenVerificationService.verifyExternalUser(
                requestDto.getRequesterInfo().getToken(),
                requestDto.getRequesterInfo().getOrganization()
        );
        if (!verified) {
            return ResponseEntity.status(403).body("External user verification failed for organization: " + requestDto.getRequesterInfo().getOrganization());
        }

        RequestResponse d=takeDecision(requestDto);

        PipelineProcessingElementRequest peReq=new PipelineProcessingElementRequest();
        peReq.setId(requestDto.getId());
        if(d.getRequestStatus().equals(AccessRequestStatus.APPROVED)) {
            peReq.setApprovalToken(d.getToken());
            peReq.setAllowedDurationHours(requestDto.getRequestedDurationHours());
        }
        peReq.setProcessingElement(peRepository.findByTemplateId(requestDto.getProcessingElementName())
                .orElseThrow(()->new IllegalArgumentException("Processing Element not found with Name: " + requestDto.getProcessingElementName())));
        peReq.setPipelineName(requestDto.getPipelineName());
        peReq.setRequesterInfo(requestDto.getRequesterInfo());
        peReq.setRequestedDurationHours(requestDto.getRequestedDurationHours());
        peReq.setStatus(d.getRequestStatus());
        peReq.setInstanceNumber(requestDto.getInstanceNumber());
        pipelinePeReqRepo.save(peReq);

        return ResponseEntity.ok(d);
    }
    private RequestResponse takeDecision(PipelineProcessingElementRequestOutboundDto req) {

        SubscriberOrganization org=subscriberOrganizationRepository.findByName(req.getRequesterInfo().getOrganization())
                .orElseThrow(()->new IllegalArgumentException("Organization is not a subscriber"));

        Tiers tier=tiersRepository.findByName(Tier.valueOf(org.getTier().name()))
                .orElseThrow(()->new IllegalArgumentException("Tier not found"));

        ProcessingElement pe= peRepository.findByTemplateId(req.getProcessingElementName())
                .orElseThrow(()->new IllegalArgumentException("Processing Element not found with Name: " + req.getProcessingElementName()));

        Tier orgTier=Tier.valueOf(org.getTier().name());
        Tier requiredTier=pe.getTier();

        RequestResponse response=new RequestResponse();
        if (orgTier.ordinal() >= requiredTier.ordinal()) {
            if (req.getRequestedDurationHours()<= tier.getMaxHours()) {
//                if(pe.getRiskLevel().equals("HIGH")) {
//                    response.setRequestStatus(AccessRequestStatus.PENDING);
//                    response.setToken("Processing Element has HIGH risk level, so a manual acceptance is needed.");
//                    return response;
//                }
                response.setRequestStatus(AccessRequestStatus.APPROVED);
                String token= tokenService.generateApprovalToken(req, req.getRequestedDurationHours());
                response.setToken(token);
                return response;
            } else {
                response.setRequestStatus(AccessRequestStatus.PENDING);
                response.setToken("Requested duration " + req.getRequestedDurationHours() + " exceeds max allowed " + tier.getMaxHours() + " for your tier, so a manual acceptance is needed.");
                return response;
            }
        } else {
            response.setRequestStatus(AccessRequestStatus.REJECTED);
            response.setToken("Organization tier "+orgTier+" is lower than required "+requiredTier);
            return response;
        }
    }


    @Transactional
    @PostMapping("/request-access/approve")
    public ConfirmationResponse approveRequest(@RequestBody RequestResponse requestResponse){
        var request = pipelinePeReqRepo.findById(requestResponse.getRequestId())
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if(request.getId() == null){
            var confirmationResponse = new ConfirmationResponse();
            confirmationResponse.setMessageReceived(false);
            return confirmationResponse;
        }


        // create if to check if approved to add the token
        if(requestResponse.getRequestStatus()==AccessRequestStatus.APPROVED){
            request.setApprovalToken(requestResponse.getToken());
            ValidatedPipelineConfig config = validatePipelineRepository.getPipeline(request
                    .getPipelineName());
                if (config != null) {
                    config.getExternalPEsTokens().put(request.getProcessingElement().getTemplateId(), tokenService.signExistingToken(requestResponse.getToken(),300));
                } else {
                    throw new IllegalArgumentException("Pipeline not found: " + request.getPipelineName());
                }

        }
        request.setApprovalToken(requestResponse.getToken());
        request.setStatus(requestResponse.getRequestStatus());

        pipelinePeReqRepo.save(request);

        var confirmationResponse = new ConfirmationResponse();
        confirmationResponse.setMessageReceived(true);
        if ("VALID".equals(getConfiguration(request.getPipelineName()).getStatus())) {
            Pipeline pipeline = pipelineRepository.findByName(request.getPipelineName())
                    .orElseThrow(() -> new IllegalArgumentException("Pipeline not found: " + request.getPipelineName()));
            pipeline.setPipelinePhase(PipelinePhase.CONFIGURED);
            pipelineRepository.save(pipeline);
        }
        return confirmationResponse;
    }




    private ConfigureValidationDto getConfiguration(String pipelineName) {
        Pipeline pipeline = pipelineRepository.findByName(pipelineName).
                orElseThrow(() -> new IllegalArgumentException("Pipeline Not Found"));

        String orgName = pipeline.getOwnerOrganization().getName();

        // Collect partner-owned processing elements with their org names
        var partnerElements = pipeline.getProcessingElements().stream()
                .filter(pe ->
                        pe.getOwnerPartnerOrganization() != null
                                && pe.getOwnerOrganization() == null
                                && !pe.getOwnerPartnerOrganization().getName().equalsIgnoreCase(orgName)
                )
                .map(pe -> new MissingPermissionsDto(
                        pe.getTemplateId(),
                        pe.getOwnerPartnerOrganization().getName()))
                .toList();

        ConfigureValidationDto validationDto = new ConfigureValidationDto();
        if (partnerElements.isEmpty()) {
            validationDto.setStatus("VALID");
            validationDto.setMissingPermissions(List.of());
        } else {
            // Load actual partner-owned elements
            var partnerElementsEntities = pipeline.getProcessingElements().stream()
                    .filter(pe ->
                            pe.getOwnerPartnerOrganization() != null
                                    && pe.getOwnerOrganization() == null
                                    && !pe.getOwnerPartnerOrganization().getName().equalsIgnoreCase(orgName)
                    )
                    .collect(Collectors.toList());

            // Collect missing elements: those with no PENDING request
            List<MissingPermissionsDto> missingPermissions = partnerElementsEntities.stream()
                    .filter(pe -> !pipelinePeReqRepo
                            .existsByPipelineNameAndPipelineProcessingElementInstanceAndStatus(
                                    pipeline.getName(),          // matches column pipeline_id
                                    pipelinePeInstanceRepo.findByPipelineAndProcessingElement(pipeline, pe)
                                            .orElseThrow(() -> new IllegalStateException("Pipeline PE Instance not found")),      // match the specific instance
                                    AccessRequestStatus.APPROVED))
                    .map(pe -> new MissingPermissionsDto(
                            pe.getTemplateId(),
                            pe.getOwnerPartnerOrganization().getName()))
                    .collect(Collectors.toList());

            if (missingPermissions.isEmpty()) {
                validationDto.setStatus("VALID");
                validationDto.setMissingPermissions(List.of());
            } else {
                validationDto.setStatus("INVALID");
                validationDto.setMissingPermissions(missingPermissions);
            }
        }

        return validationDto;


    }

}