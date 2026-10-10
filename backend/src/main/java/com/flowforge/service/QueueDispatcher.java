package com.flowforge.service;

import com.flowforge.model.Job;
import com.flowforge.model.JobStatus;
import com.flowforge.repository.JobRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueDispatcher {
    public static final String QUEUE_KEY="flowforge:jobs";
    private final JobRepository repository; private final StringRedisTemplate redis;
    public QueueDispatcher(JobRepository repository,StringRedisTemplate redis){this.repository=repository;this.redis=redis;}

    // PostgreSQL is the durable source of truth. Re-publishing queued IDs recovers work after Redis/app restarts.
    // Duplicate queue entries are expected and safely rejected by the worker's guarded state transition.
    @Scheduled(fixedDelayString="${flowforge.dispatcher-delay-ms:2000}")
    public void dispatch(){
        try { for(Job job:repository.findTop100ByStatusOrderByCreatedAtAsc(JobStatus.QUEUED)) redis.opsForList().leftPush(QUEUE_KEY,job.getId().toString()); }
        catch(Exception e){System.err.println("Queue dispatch failed: "+e.getMessage());}
    }
}
