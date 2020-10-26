package com.example.account.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;

@RestController
@RequestMapping("")
public class InfoController {

    @Autowired
    Environment environment;

    @GetMapping
    public Mono<LinkedHashMap<String, String>> index() throws UnknownHostException {
        return info();
    }

    @GetMapping("/info")
    public Mono<LinkedHashMap<String, String>> info() throws UnknownHostException {

        InetAddress localhost = InetAddress.getLocalHost();

        LinkedHashMap<String, String> response = new LinkedHashMap<>();
        response.put("containerName", environment.getProperty("app.container-name"));
        response.put("port", environment.getProperty("server.port"));
        response.put("hostAddress", localhost.getHostAddress());
        response.put("hostName", localhost.getHostName());
        response.put("date", LocalDateTime.now().toString());

        return Mono.just(response);
    }
}
