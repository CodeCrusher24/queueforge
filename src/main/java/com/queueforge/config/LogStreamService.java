package com.queueforge.config;

import org.springframework.stereotype.Service;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Service
public class LogStreamService {
    private final LinkedList<String> logs = new LinkedList<>();
    private static final int MAX_LOGS = 100;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public synchronized void addLog(String message) {
        String time = LocalTime.now().format(formatter);
        logs.addFirst("[" + time + "] " + message);
        if (logs.size() > MAX_LOGS) {
            logs.removeLast();
        }
    }

    public synchronized List<String> getLogs() {
        return new ArrayList<>(logs);
    }
}