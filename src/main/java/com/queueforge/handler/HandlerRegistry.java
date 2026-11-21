package com.queueforge.handler;

import com.queueforge.job.JobType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class HandlerRegistry {

    private final Map<JobType, JobHandler> handlers;

    public HandlerRegistry(List<JobHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(JobHandler::supports, Function.identity()));
    }

    public JobHandler getHandler(JobType type) {
        JobHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for type: " + type);
        }
        return handler;
    }
}