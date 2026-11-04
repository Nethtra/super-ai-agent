package com.wty.superaiagent.dto;

import java.util.List;

/**19
 * @author 王天一
 * @version 1.0
 */
public class SearchFetchDTO {
    // 请求体
    public record DeepSeekSearchRequest(
            String query,
            Integer count,
            String ori_question,
            Boolean need_content
    ) {}

    // 响应体
    public record DeepSeekSearchResponse(
            List<SearchResult> results
    ) {
        public record SearchResult(
                Double rerank_score,
                String title,
                String url,
                String snippet,
                String summary,
                String content,
                String site_name,
                String site_icon,
                String date,
                Integer authority
        ) {}
    }
    public record DeepSeekFetchRequest(
            String url
    ) {}

    public record DeepSeekFetchResponse(
            String title,
            String content
    ) {}
}
