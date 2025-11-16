package com.exploration.circuitbreaker.advance;

import org.springframework.stereotype.Service;

@Service
public class AdvanceFlakyService {

    public String callSlow() {
        return "Slow Call";
    };

    public String callWithCustomErrors() {
        return "Custom Error";
    };

    public String callWithIgnorableErrors() {
        return "Ignorable Error";
    };

}
