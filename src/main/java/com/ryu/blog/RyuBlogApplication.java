package com.ryu.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.tools.agent.ReactorDebugAgent;

/**
 * 单体博客应用程序入口类
 * @author ryu
 */
@SpringBootApplication
public class RyuBlogApplication {

    public static void main(String[] args) {
        // 启用Reactor调试代理，用于开发环境的调试
        ReactorDebugAgent.init();
        SpringApplication.run(RyuBlogApplication.class, args);
    }
} 