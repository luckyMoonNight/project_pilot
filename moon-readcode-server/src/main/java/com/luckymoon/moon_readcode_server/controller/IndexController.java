package com.luckymoon.moon_readcode_server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径转发到 index.html，让访问 http://localhost:8080/ 就能直接打开聊天页面。
 */
@Controller
public class IndexController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
