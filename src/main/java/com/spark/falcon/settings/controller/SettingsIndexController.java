package com.spark.falcon.settings.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SettingsIndexController {

    @GetMapping("/owner/settings")
    public String index() {
        return "redirect:/owner/settings/general";
    }
}
