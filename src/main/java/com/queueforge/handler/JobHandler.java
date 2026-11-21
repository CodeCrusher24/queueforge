package com.queueforge.handler;

import com.queueforge.job.JobType;
import java.util.Map;

public interface JobHandler {
    JobType supports();
    void validate(Map<String, Object> payload);
    void execute(Map<String, Object> payload) throws Exception;
}