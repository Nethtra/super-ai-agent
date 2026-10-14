package com.wty.superaiagent.demo.invoke;
/**
 * @author 王天一
 * @version 1.0
 * 测试http方式 curl方式调用api
 */

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;

public class CurlAiInvoke {

    public static void main(String[] args) {
        // 1. 替换成你自己的 API Key
        String apiKey = "";
        String url = "https://api.siliconflow.cn/v1/chat/completions";

        // 2. 构建请求体 JSON（用 Hutool 的 JSONUtil 构建，避免手动拼接字符串）
        String jsonBody = JSONUtil.createObj()
                .set("model", "deepseek-ai/DeepSeek-V4-Flash")
                .set("messages", JSONUtil.createArray()
                        .set(JSONUtil.createObj()
                                .set("role", "system")
                                .set("content", "你是一个有用的助手"))
                        .set(JSONUtil.createObj()
                                .set("role", "user")
                                .set("content", "你好，请介绍一下你自己"))
                )
                .toString();

        // 3. 发送 POST 请求
        try (HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .body(jsonBody)
                .timeout(30000) // 设置超时时间（毫秒），大模型响应可能较慢
                .execute()) {

            // 4. 打印结果
            System.out.println("状态码: " + response.getStatus());
            System.out.println("响应内容: " + response.body());
        }
    }
}