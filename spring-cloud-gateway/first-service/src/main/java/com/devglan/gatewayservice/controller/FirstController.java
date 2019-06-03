package com.devglan.gatewayservice.controller;

import org.springframework.web.bind.annotation.*;

@RestController
public class FirstController {

    @GetMapping("/test")
    public String test(@RequestHeader("X-first-Header") String headerValue){
        return headerValue;
    }

}
