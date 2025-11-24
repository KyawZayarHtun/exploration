package com.exploration.retry;

import org.springframework.stereotype.Service;

@Service
public class GoofyService {

    public String getData() {
        return "Data from goofy service";
    }

}
