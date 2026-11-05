package com.wty.superaiagent.config;

import com.wty.superaiagent.tools.*;
import jakarta.annotation.Resource;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 24 工具注册配置：把各个工具类里的 {@code @Tool} 方法统一注册成容器里的 {@link ToolCallback} 数组。
 * <p>
 * 一、这里在做什么
 * 工具类（{@link FileOperationTool} 等）只是"写了 {@code @Tool} 注解的普通类"，
 * 注解本身不会让模型认识它们。要让模型能用，必须先把它们转换成 {@link ToolCallback}：
 * 一个 ToolCallback 里打包了方法名、description、参数 JSON Schema 和真正的执行逻辑，
 * 即模型看到的"工具说明书"。
 * <p>
 * {@link ToolCallbacks#from(Object...)} 就是做这件事的：扫描传入对象上所有 {@code @Tool} 方法，
 * 逐个生成 ToolCallback。所以这里不需要给每个方法写一行代码。
 * <p>
 * 二、为什么做成 {@code @Bean} 而不是每次 new
 * <ul>
 *     <li>ToolCallback 数组会被注入到 ChatClient 的构建过程里，多个地方要用同一份；</li>
 *     <li>做成 Bean 后只创建一次（容器单例），避免每次对话调用都重复做反射扫描；</li>
 *     <li>方便在测试里用 {@code @MockBean} 替换掉整组工具。</li>
 * </ul>
 * <p>
 * 三、为什么只有 WebSearchFetchTool 是注入，其它都是 new
 * {@link WebSearchFetchTool} 需要读取配置（服务地址、密钥）才能构造，所以它标了 {@code @Component}，
 * 由容器创建后注入到这里；其余工具没有依赖、也没有状态，直接 new 即可。
 * <p>
 * 四、使用方式
 * 在 ChatClient 构建时传入这个数组（字段注入 {@code ToolCallback[] allTools} 后）：
 * <pre>
 *   ChatClient.builder(chatModel).defaultTools(allTools).build();
 *   或者在单次请求上：chatClient.prompt().tools(allTools)
 * </pre>
 * <p>
 * 五、使用注意（踩坑点）
 * <ul>
 *     <li><b>工具名不要重复</b>：ToolCallback 的名称默认取方法名，两个类里都有 {@code searchWeb}
 *     会让模型难以区分甚至互相覆盖，方法名要保持全局唯一。</li>
 *     <li><b>工具越多越"贵"</b>：每个工具的 description 和参数说明都会随请求发给模型，
 *     既消耗 token 又降低选择准确率，一般控制在 10 个以内。</li>
 *     <li><b>TerminalOperationTool 注册即等于把 shell 交给模型</b>：演示可以，
 *     接入线上前建议加 {@code @Profile("dev")} 或直接从这个数组里去掉。</li>
 *     <li><b>工具实例被共享</b>：这些对象会被并发调用，不要在工具类里保存可变状态。</li>
 *     <li>Spring AI 2.0 里 {@link ToolCallbacks} 位于 {@code org.springframework.ai.support} 包；
 *     1.0 中同类需求是 {@code ToolCallbacks.from(...)} 之外的 {@code MethodToolCallbackProvider}，升级时注意包名变化。</li>
 * </ul>
 *
 * @author 王天一
 * @version 1.0
 */
@Configuration
public class ToolConfig {


    @Resource
    public WebSearchFetchTool webSearchFetchTool;

    /**
     * 把所有工具方法转成 ToolCallback 数组并交给容器保管。
     */
    @Bean
    public ToolCallback[] allTools() {
        // 无依赖的工具：直接 new，构造开销可以忽略，也便于单独测试
        CalculationTool calculationTool = new CalculationTool();
        FileOperationTool fileOperationTool = new FileOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();

        // 反射扫描上述对象里的 @Tool 方法，生成"方法名 + 描述 + 参数schema + 执行逻辑"
        // 没贴 @Tool 的方法会被忽略，所以工具类里可以放心写私有辅助方法
        return ToolCallbacks.from(
                webSearchFetchTool,       // 来自容器，带配置
                calculationTool,
                fileOperationTool,
                pdfGenerationTool,
                resourceDownloadTool,
                terminalOperationTool
        );
    }
}
