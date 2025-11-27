package com.exploration.retry;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GoofyService {

    public String getData() {
        return "Data from goofy service";
    }

    public ResponseEntity<String> get503RestException() {
        RestClient restClient = RestClient.builder().baseUrl("https://dummyjson.com/http/503")
                .build();
        return restClient.get().retrieve().toEntity(String.class);
    }

}
