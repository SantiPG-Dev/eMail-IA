package com.emailai.web.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.emailai.config.AppConfigStore;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AppConfigStore configStore;

    public ConfigController(AppConfigStore configStore) {
        this.configStore = configStore;
    }

    @GetMapping
    public Map<String, String> obtener(@RequestParam String key,
                                        @RequestParam(defaultValue = "") String defaultValue) {
        return Map.of("key", key, "value", configStore.get(key, defaultValue));
    }

    @PostMapping
    public void guardar(@RequestParam String key, @RequestParam String value) {
        configStore.put(key, value);
    }
}
