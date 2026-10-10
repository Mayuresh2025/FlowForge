package com.flowforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.dto.CreateJobRequest;
import com.flowforge.model.Job;
import com.flowforge.model.JobStatus;
import com.flowforge.repository.JobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;

@Service
public class JobService {
    private final JobRepository repository; private final ObjectMapper mapper;
    public JobService(JobRepository repository, ObjectMapper mapper){this.repository=repository;this.mapper=mapper;}

    public Job create(CreateJobRequest request) {
        String type=request.type().trim().toUpperCase();
        if(!Map.of("ECHO",true,"WAIT",true,"FAIL",true).containsKey(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Supported job types: ECHO, WAIT, FAIL");
        try { return repository.save(new Job(type,mapper.writeValueAsString(request.payload()),request.maxAttempts()==null?3:request.maxAttempts())); }
        catch(JsonProcessingException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payload must be valid JSON");}
    }
    public Page<Job> list(int page,int size){return repository.findAll(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt")));}
    public Job get(UUID id){return repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found"));}
    public Job cancel(UUID id){Job job=get(id); if(job.getStatus()!=JobStatus.QUEUED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only queued jobs can be cancelled"); job.cancel(); return repository.save(job);}
    public Map<String,Long> stats(){return Map.of("queued",repository.countByStatus(JobStatus.QUEUED),"running",repository.countByStatus(JobStatus.RUNNING),"succeeded",repository.countByStatus(JobStatus.SUCCEEDED),"failed",repository.countByStatus(JobStatus.FAILED),"cancelled",repository.countByStatus(JobStatus.CANCELLED));}
}
