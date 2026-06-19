package com.FBLA.WebCodingDev26Backend.service;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ClockService {
    public String now() {
        return Instant.now().toString();
    }
}
