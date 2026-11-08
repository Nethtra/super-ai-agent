package com.wty.imgsearchmcpserver.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;


import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**27开发mcp服务器 搜索图片    本质还是Tool   使用pexafy api
 * @author 王天一
 * @version 1.0
 */
@Service
public class ImgSearchTool {

    @Value("${PEXAFY_API_KEY}")
    private String apiKey;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Tool(description = "搜索免费商用图库。当用户需要配图、找照片或需要视觉素材时使用。请用完整的场景描述（如 'a developer drinking coffee in a dark room'）而不是零散的关键词。")
    public String searchPhotos(
            @ToolParam(description = "完整的自然语言场景描述，最多500字符") String query
    ) {
        if (query == null || query.isBlank()) {
            return "搜索描述不能为空。";
        }

        String url = "https://api.pexafy.com/api/v1/search/photos";

        try {
            // 1. 发起 GET 请求 (使用 Hutool)
            HttpResponse response = HttpRequest.get(url)
                    .header("x-api-key", apiKey)
                    .form("q", query)
                    .form("per_page", 5) // 默认返回5张，防止上下文过长
                    .timeout(10000)      // 10秒超时
                    .execute();

            if (!response.isOk()) {
                return "图片搜索失败，HTTP 状态码：" + response.getStatus() + "，响应：" + response.body();
            }

            // 2. 解析响应 (使用 Jackson 3)
            JsonNode rootNode = jsonMapper.readTree(response.body());

            // 3. 检查业务成功状态
            if (!rootNode.path("success").asBoolean()) {
                return "Pexafy API 返回业务错误：" + rootNode.path("error").toString();
            }

            JsonNode dataArray = rootNode.path("data");
            if (dataArray.isEmpty()) {
                return "未找到与描述匹配的图片。";
            }

            // 4. 提取模型真正需要的数据，拼装为 Markdown 文本
            StringBuilder result = new StringBuilder("为您找到以下图片：\n\n");
            for (JsonNode photo : dataArray) {
                String imageUrl = photo.path("urls").path("regular").stringValue();
                String description = photo.path("alt_description").stringValue();
                String credit = photo.path("attribution").path("plain").stringValue();

                result.append("**图片描述**: ").append(description).append("\n")
                        .append("**图片链接**: ").append(imageUrl).append("\n")
                        .append("**版权署名**: ").append(credit).append("\n")
                        .append("---\n");
            }
            return result.toString();

        } catch (Exception e) {
            // 捕获网络异常、解析异常，返回给模型，避免整个 MCP 链路崩溃
            return "图片搜索过程中发生异常：" + e.getMessage();
        }
    }
}

