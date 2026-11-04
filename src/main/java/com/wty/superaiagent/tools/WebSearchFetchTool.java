package com.wty.superaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import com.wty.superaiagent.dto.SearchFetchDTO.*;

import java.util.stream.Collectors;

/**
 * 19 联网搜索、抓取工具 使用ds search fetch api
 *
 *     <li><b>请求/响应用 DTO 而不是 Map 和 JsonNode</b>：DTO 定义在
 *     {@link com.wty.superaiagent.dto.SearchFetchDTO} 中，都是 record。
 *     好处是字段类型明确、IDE 能提示、少写取值代码，坏处是服务端字段改名时本地要跟着改。</li>
 *     <li><b>record 如何与 JSON 对应</b>：Jackson 3 能直接用 record 的构造器反序列化，
 *     要求字段名与 JSON 一致。这里刻意保留了服务端的下划线命名
 *     （{@code need_content}、{@code rerank_score}、{@code site_name}），
 *     所以不需要额外加注解；如果把字段改成驼峰，就要用 {@code @JsonProperty} 或配置命名策略。</li>
 *     <li><b>抓取工具会截断正文</b>：网页正文动辄几万字，全部塞进上下文会超出模型限制，
 *     因此超过 5000 字符时截断并加提示。</li>
 * <p>
 * 四、使用注意（踩坑点）
 * <ul>
 *     <li><b>没有设置超时</b>：{@code RestClient} 默认使用 JDK 的 HTTP 客户端，
 *     网络异常时可能长时间阻塞；工具调用是同步的，会拖住整个对话。
 *     生产环境应显式设置连接超时与读取超时。</li>
 *     <li><b>出错返回字符串而非抛异常</b>：抛异常会中断调用链，模型什么都拿不到；
 *     返回 "xxx失败：原因" 让模型知道失败并自行决定怎么回答用户。</li>
 *     <li><b>返回值会占用上下文</b>：工具结果原样进入模型上下文，所以搜索固定取 5 条、
 *     抓取正文做了截断。</li>
 * </ul>
 *
 * @author 王天一
 * @version 1.0
 */
@Component
public class WebSearchFetchTool {

    /**
     * 调用搜索/抓取服务的 HTTP 客户端：构造时创建一次，后续复用（RestClient 线程安全）
     */
    private final RestClient restClient;

    /**
     * 构造器注入服务地址与密钥，并初始化 RestClient。
     *
     * @param baseUrl 服务基础地址
     * @param apiKey  访问密钥，请求时放在 Authorization 头里
     */
    public WebSearchFetchTool(
            @Value("${spring.ai.deepseek.base-url}") String baseUrl,
            @Value("${spring.ai.deepseek.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                // 认证头和内容类型对所有请求生效，不必在每个方法里重复设置
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 联网搜索（工具一）：只返回每条结果的标题、链接、摘要。
     *
     * @param query 搜索关键词，由模型从用户提问中提炼
     * @return 多条结果拼接成的文本，结果之间用分隔线隔开；无结果或出错时返回提示文本
     */
    @Tool(description = "搜索互联网获取实时信息...")
    public String searchWeb(@ToolParam(description = "...") String query) {
        // 请求体是 record，Jackson 会自动序列化成 JSON：
        //   query       关键词
        //   count       返回条数，控制塞进上下文的长度
        //   ori_question 原始问题，服务端用于相关性重排；这里不传（null）
        //   need_content 是否返回网页正文，false 表示只要摘要（正文请用 fetchWebContent 抓）
        var request = new DeepSeekSearchRequest(query, 5, null, false);

        try {
            // post 发请求 → retrieve 拿到响应 → body 用 DTO 接收，Jackson 自动反序列化
            // 注意：4xx/5xx 状态下 retrieve() 会直接抛异常，由下面 catch 兜住
            var response = restClient.post()
                    .uri("/v1/tools/search")
                    .body(request)
                    .retrieve()
                    .body(DeepSeekSearchResponse.class);

            // 三层判空：响应体可能为 null、results 字段可能缺失、也可能是空数组
            if (response == null || response.results() == null || response.results().isEmpty()) {
                return "未找到相关网页结果。";
            }

            // 拼成"标题/链接/摘要"格式的文本：模型读的是自然语言，带标签更易区分字段
            // summary 为空时退回 snippet（两者是服务端返回的两种摘要字段）
            return response.results().stream()
                    .map(r -> String.format("标题：%s\n链接：%s\n摘要：%s",
                            r.title(),
                            r.url(),
                            r.summary() != null ? r.summary() : r.snippet()))
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            return "搜索执行失败：" + e.getMessage();
        }
    }

    /**
     * 抓取网页正文（工具二）：把指定 URL 的页面转成 Markdown 文本返回。
     * <p>
     * 典型用途：搜索得到若干链接后，模型挑一条再调用本工具拿到完整正文。
     *
     * @param url 要抓取的网页地址，必须以 http:// 或 https:// 开头
     * @return 网页标题 + 正文（超长会截断）；参数非法、响应为空或出错时返回提示文本
     */
    @Tool(description = "抓取指定URL的网页内容（Markdown格式）...")
    public String fetchWebContent(@ToolParam(description = "...") String url) {
        // 参数校验：模型可能传空值或漏写协议头，直接返回可读的失败原因让它自己纠正
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return "抓取失败：URL格式不正确。";
        }

        var request = new DeepSeekFetchRequest(url);

        try {
            var response = restClient.post()
                    .uri("/v1/tools/fetch")
                    .body(request)
                    .retrieve()
                    .body(DeepSeekFetchResponse.class);

            // isBlank() 同时覆盖 "" 和全是空格的情况
            if (response == null || response.content() == null || response.content().isBlank()) {
                return "抓取失败：未能提取到网页正文。";
            }

            String content = response.content();
            // 截断防爆 Token：这里按"字符数"截断，中文大致一字一 token，5000 字符约 5k token
            if (content.length() > 5000) {
                content = content.substring(0, 5000) + "\n\n...[内容过长，已截断]";
            }

            return String.format("网页标题：%s\n\n网页正文：\n%s", response.title(), content);

        } catch (Exception e) {
            return "抓取执行失败：" + e.getMessage();
        }
    }
}
