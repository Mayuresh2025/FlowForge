package com.flowforge.controller;

import com.flowforge.dto.CreateJobRequest;
import com.flowforge.dto.JobResponse;
import com.flowforge.model.Job;
import com.flowforge.service.JobService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api")
public class JobController {
    private final JobService service; public JobController(JobService service){this.service=service;}
    @PostMapping("/jobs") @ResponseStatus(HttpStatus.CREATED) public JobResponse create(@Valid @RequestBody CreateJobRequest request){return JobResponse.from(service.create(request));}
    @GetMapping("/jobs") public Map<String,Object> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        if(page<0||size<1||size>100)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"page must be >= 0 and size must be 1..100");
        Page<Job> result=service.list(page,size);return Map.of("content",result.getContent().stream().map(JobResponse::from).toList(),"page",result.getNumber(),"size",result.getSize(),"totalElements",result.getTotalElements(),"totalPages",result.getTotalPages());
    }
    @GetMapping("/jobs/{id}") public JobResponse get(@PathVariable UUID id){return JobResponse.from(service.get(id));}
    @PostMapping("/jobs/{id}/cancel") public JobResponse cancel(@PathVariable UUID id){return JobResponse.from(service.cancel(id));}
    @GetMapping("/stats") public Map<String,Long> stats(){return service.stats();}
}
