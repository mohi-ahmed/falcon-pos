package com.spark.falcon.identity.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicPageController {

    @GetMapping("/")
    String landingPage() {
        return "public/index";
    }

    @GetMapping("/terms")
    String terms() {
        return "public/terms";
    }

    @GetMapping("/privacy")
    String privacy() {
        return "public/privacy";
    }
}

