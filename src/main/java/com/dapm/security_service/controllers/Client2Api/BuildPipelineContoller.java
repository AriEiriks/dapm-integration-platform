package com.dapm.security_service.controllers.Client2Api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import candidate_validation.ValidatedPipeline;
import com.dapm.security_service.models.Pipeline;
import com.dapm.security_service.models.dtos2.designpipeline.DesignPipelineDto;
import com.dapm.security_service.models.enums.PipelinePhase;
import com.dapm.security_service.models.enums.ProjectPermAction;
import com.dapm.security_service.models.models2.ValidatedPipelineConfig;
import com.dapm.security_service.repositories.PipelineRepositoryy;
import com.dapm.security_service.repositories.ValidatePipelineRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pipeline.PipelineBuilder;
import pipeline.service.PipelineExecutionService;

@RestController
@RequestMapping("/api/build-pipeline")
public class BuildPipelineContoller {
    private static final Logger log =
            LoggerFactory.getLogger(BuildPipelineContoller.class);
    @Autowired private PipelineRepositoryy pipelineRepository;
    @Autowired private ValidatePipelineRepository validatePipelineRepository;
    @Autowired private PipelineBuilder pipelineBuilder;

    @Autowired private PipelineExecutionService executionService;


    // create endpoint to build pipeline by sending pipeline name
    @PreAuthorize("@pipelineAccessEvaluator.hasPermission(#pipelineName, authentication, 'BUILD_PIPELINE')")
    @PostMapping("/{pipelineName}")
    public ResponseEntity<?> buildPipeline(
            @PathVariable String pipelineName
    ) throws JsonProcessingException {
        // find pipeline by name
        Pipeline pipeline = pipelineRepository.findByName(pipelineName).
                orElseThrow(() -> new RuntimeException("Pipeline not found"));
        if (pipeline.getPipelinePhase().equals(PipelinePhase.BUILT)){
            return ResponseEntity.badRequest().body("Pipeline is already built");
        }

        System.out.println("[BUILD] pipeline=" + pipelineName + " phase=" + pipeline.getPipelinePhase());

        if (!pipeline.getPipelinePhase().equals(PipelinePhase.CONFIGURED)){
            // return bad request
            return ResponseEntity.badRequest().body("Pipeline is not in CONFIGURED phase");
        }
        ValidatedPipelineConfig validatedPipeline = validatePipelineRepository.getPipeline(pipelineName);
        if (validatedPipeline == null){
            return ResponseEntity.badRequest().body("Pipeline is not validated");
        }

        try {

            pipelineBuilder.buildPipeline(
                    pipelineName,
                    validatedPipeline.getValidatedPipeline(),
                    validatedPipeline.getExternalPEsTokens()
            );

        } catch (Exception e) {
            log.error("BUILD_PIPELINE failed for pipelineName={}", pipelineName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Build failed: " + e.getMessage());
        }


        //pipelineBuilder.buildPipeline(validatedPipeline.getPipelineName(), validatedPipeline.getValidatedPipeline(), validatedPipeline.getExternalPEsTokens());
        // update pipeline phase to BUILT



        pipeline.setPipelinePhase(PipelinePhase.BUILT);
        pipelineRepository.save(pipeline);
        // return ok
        return ResponseEntity.ok("Pipeline built successfully");
    }




    @PreAuthorize("@pipelineAccessEvaluator.hasPermission(#pipelineName, authentication, 'EXECUTE_PIPELINE')")
    @PostMapping("/execute/{pipelineName}")
    public ResponseEntity<?> executePipeline(
            @PathVariable String pipelineName
    ) throws JsonProcessingException {
        Pipeline pipeline = pipelineRepository.findByName(pipelineName).
                orElseThrow(() -> new RuntimeException("Pipeline not found"));
        executionService.start(pipelineName);
        pipeline.setPipelinePhase(PipelinePhase.EXECUTING);
        pipelineRepository.save(pipeline);

        return ResponseEntity.ok("Pipeline execution started successfully");
    }

    @PreAuthorize("@pipelineAccessEvaluator.hasPermission(#pipelineName, authentication, 'TERMINATE_PIPELINE')")
    @PostMapping("/terminate/{pipelineName}")
    public ResponseEntity<?> terminatePipeline(
            @PathVariable String pipelineName
    ) throws JsonProcessingException {
        Pipeline pipeline = pipelineRepository.findByName(pipelineName).
                orElseThrow(() -> new RuntimeException("Pipeline not found"));
        executionService.terminate(pipelineName);
        pipeline.setPipelinePhase(PipelinePhase.TERMINATED);
        pipelineRepository.save(pipeline);
        return ResponseEntity.ok("Pipeline terminated successfully");
    }




}
