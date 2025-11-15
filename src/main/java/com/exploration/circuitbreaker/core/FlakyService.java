package com.exploration.circuitbreaker.core;

import org.springframework.stereotype.Service;

@Service
public class FlakyService {

    public String getData() {
        return "Data from remote service";
    }

}
