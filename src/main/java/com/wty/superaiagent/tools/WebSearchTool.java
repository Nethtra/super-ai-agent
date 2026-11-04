package com.wty.superaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 19 联网搜索工具 使用ds search api

 * 二、本类与 FileOperationTool 的一个区别
 * 这里要读配置（搜索服务的地址和密钥），所以加了 {@code @Component} 交给容器管理，
 * 使用时通过 {@code .tools(webSearchTool)} 注入；而纯逻辑、无依赖的工具类可以不进容器，直接 new。
 * <p>
 * 三、请求与响应的组织方式
 * <ul>
 *     <li>用 {@link RestClient} 发 HTTP 请求：它是 Spring 提供的同步 HTTP 客户端，
 *     线程安全，构建一次可以反复使用（所以放在构造器里创建并保存为 final 字段）；</li>
 *     <li>请求体用 {@code Map} 构造，由 JSON 序列化器自动转成 JSON，不必写一个 DTO 类；</li>
 *     <li>响应直接映射成 {@link JsonNode}（JSON 树模型），用 {@code path("字段名")} 逐层取值，
 *     适合字段结构不确定或只需要几个字段的场景。</li>
 * </ul>
 * <p>
 * 四、使用注意（踩坑点）
 * <ul>
 *     <li><b>没有设置超时</b>：{@code RestClient} 默认使用 JDK 的 HTTP 客户端，
 *     网络异常时可能长时间阻塞，而工具调用是同步的，会拖住整个对话。
 *     生产环境应显式设置连接超时和读取超时。</li>
 *     <li><b>返回值长度</b>：结果会原样进入模型上下文，{@code count} 调大或返回网页正文时
 *     很容易超出上下文限制，所以这里固定取 5 条、且 {@code need_content=false} 只要摘要。</li>
 *     <li><b>出错返回字符串而非抛异常</b>：工具方法抛异常会中断调用链，模型什么也拿不到；
 *     返回 "搜索执行失败：xxx" 让模型知道失败并自行决定怎么回复用户。</li>
 * </ul>
 *
 * @author 王天一
 * @version 1.0
 */
@Component
public class WebSearchTool {

    /**
     * 调用搜索服务用的 HTTP 客户端，构造时按配置里的地址和密钥创建一次，后续复用
     */
    private final RestClient restClient;

    /**
     * 构造器注入搜索服务的地址与密钥，并完成 RestClient 的初始化。
     * <p>
     * {@code @Value} 用于把配置文件中的值注入到参数上（这里取的是
     * {@code application.yml} 里 {@code spring.ai.deepseek.base-url} 与
     * {@code spring.ai.deepseek.api-key} 两项）。
     *
     * @param baseUrl 搜索服务的基础地址，例如 https://api.deepseek.com
     * @param apiKey  访问密钥，请求时放在 Authorization 头里
     */
    public WebSearchTool(
            @Value("${spring.ai.deepseek.base-url}") String baseUrl,
            @Value("${spring.ai.deepseek.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                // 认证头 + 内容类型：所有请求都会自动带上，不必每个方法重复设置
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * @return 拼接好的文本结果（标题 + 链接 + 摘要），每条之间用分隔线隔开；
     *         没有结果时返回提示语，请求出错时返回错误描述
     */
    @Tool(description = "搜索互联网获取实时信息。当用户询问最新新闻、实时数据或你的知识库中没有的内容时，请调用此工具。")
    public String searchWeb(
            @ToolParam(description = "搜索关键词，支持高级语法，如 site:github.com、lang:zh") String query
    ) {
        // 1. 直接用 Map 构建请求体（Jackson 会自动转成 JSON）
        //    count：返回条数，控制上下文长度；need_content：是否返回网页正文，false 表示只要摘要
        Map<String, Object> requestBody = Map.of(
                "query", query,
                "count", 5,
                "need_content", false
        );

        try {
            // 2. 发起请求，直接将响应映射为 Jackson 的 JsonNode
            // JsonNode 能像操作树一样读取 JSON，不需要建类
            // 注：这里的 JsonNode 来自 tools.jackson 包，即 Jackson 3（Spring Boot 4 已从 com.fasterxml.jackson 迁移过来）
            JsonNode response = restClient.post()
                    .uri("/v1/tools/search")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            // 3. 判空与提取数据
            //    响应体可能为 null；results 字段可能不存在（服务端返回错误结构时）；也可能是空数组
            if (response == null || !response.has("results") || response.get("results").isEmpty()) {
                return "未找到相关网页结果。";
            }

            // 4. 遍历 results 数组，提取我们需要的字段拼接
            //    之所以拼成带"标题/链接/摘要"标签的文本，是因为模型读的是自然语言，标签能帮它区分字段
            StringBuilder sb = new StringBuilder();
            JsonNode results = response.get("results");

            for (JsonNode item : results) {
                // path() 取不到字段时返回"缺失节点"而不是抛异常；stringValue(默认值) 再给一层兜底
                String title = item.path("title").stringValue("无标题");
                String url = item.path("url").stringValue("");
                String summary = item.path("summary").stringValue("");

                // 如果 summary 为空，尝试用 snippet 兜底
                // 不同搜索服务返回的摘要字段名不一致，这里兼容两种
                if (summary.isEmpty()) {
                    summary = item.path("snippet").stringValue("");
                }

                sb.append("标题：").append(title).append("\n")
                        .append("链接：").append(url).append("\n")
                        .append("摘要：").append(summary).append("\n\n---\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            // 网络异常、4xx/5xx（RestClient 默认会对 4xx/5xx 抛异常）、JSON 解析失败都会走到这里
            return "搜索执行失败：" + e.getMessage();
        }
    }
}
