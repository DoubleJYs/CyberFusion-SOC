package com.zhangjiyan.template.module.demo;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class DemoService {

    public Map<String, Object> health() {
        return Map.of("name", "template-001-springboot-vue-admin", "time", LocalDateTime.now().toString());
    }
}
