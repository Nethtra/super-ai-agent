package com.wty.imgsearchmcpserver;

import com.wty.imgsearchmcpserver.tools.ImgSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ImgSearchMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImgSearchMcpServerApplication.class, args);
    }

    //启动类本身也带有 @Configuration 的语义
    @Bean
    public ToolCallbackProvider imgSearchTools(ImgSearchTool imgSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imgSearchTool)
                .build();
    }

}
