package com.wty.superaiagent.tools;

import com.wty.superaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;

/**
 * 21 资源下载工具
 * <p>
 * 下载完成后返回本地路径和文件大小，模型可以接着用
 * {@link FileOperationTool} 读取内容，或让用户自己去该目录查看。
 * <p>
 * 二、为什么用 JDK 的 HttpClient 而不是 RestClient
 * {@link org.springframework.web.client.RestClient} 更适合处理 JSON 这类小文本，
 * 用 {@code body(byte[].class)} 下载文件会把整个文件读进内存。大文件容易 OOM。
 * JDK 的 {@link HttpClient} 可以配合 {@code BodyHandlers.ofInputStream()} 边收边写磁盘，
 * 内存占用恒定，也不需要额外依赖。
 * <p>
 * 三、实现要点
 * <ul>
 *     <li><b>先看状态码再落盘</b>：如果直接把响应体写文件，404/500 的错误页面也会被存成"下载成功"；
 *     所以先判断状态码，非 2xx 直接返回失败。</li>
 *     <li><b>边下载边计数</b>：{@code Content-Length} 有时不存在（分块传输），
 *     所以除了看响应头，写入时也累计字节数，超过上限立即中断并删除半成品文件。</li>
 *     <li><b>文件名消毒</b>：只取文件名部分，去掉路径分隔符，防止模型传入
 *     {@code ../../application.yml} 把文件写到其他目录（路径穿越）。</li>
 *     <li><b>限制协议</b>：只允许 http/https，避免 {@code file://} 读本地文件。</li>
 * </ul>
 * <p>
 * 四、使用注意（踩坑点）
 * <ul>
 *     <li><b>SSRF 风险</b>：工具允许模型访问任意 URL，如果模型被恶意提示词引导，
 *     可能去请求内网地址（{@code 127.0.0.1}、{@code 192.168.x.x}）探测内网服务。
 *     这里做了基础的内网地址拦截，真实项目还应结合网关或出口白名单。</li>
 *     <li><b>超时</b>：连接超时 10 秒、整个请求 60 秒，超时会抛异常并由 catch 兜住，
 *     工具方法不能抛出异常，否则调用链会中断，模型拿不到任何结果。</li>
 *     <li><b>重名覆盖</b>：同名文件会被直接覆盖（{@code REPLACE_EXISTING}），
 *     需要保留历史版本可以给文件名加时间戳。</li>
 *     <li><b>下载目录</b>：固定为项目根目录下的 {@code tmp/download}，便于统一清理。</li>
 * </ul>
 *
 * @author 王天一
 * @version 1.0
 */
public class ResourceDownloadTool {

    /**
     * 整个请求（含读取响应体）的超时时间：大文件下载不要设得太短
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 连接超时时间：只负责"建立连接"这一步
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 单个文件大小上限（50MB）：防止模型误下载超大文件把磁盘写满
     */
    private static final long MAX_SIZE_BYTES = 50L * 1024 * 1024;

    /**
     * 下载目录：项目根目录下 tmp/download
     */
    private static final String DOWNLOAD_DIR = FileConstant.FILE_SAVE_PATH + "/download";

    /**
     * HTTP 客户端：线程安全，构建一次反复使用；自动跟随 3xx 重定向
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    /**
     * 下载远程资源并保存到本地（供大模型调用）。
     *
     * @return 成功时返回本地路径与文件大小；地址非法、被拦截、状态码异常、超限或出错时返回失败原因
     */
    @Tool(description = "根据 URL 下载网络资源（图片、PDF、文档、压缩包等）并保存到本地。"
            + "当用户提供一个文件链接并要求保存、下载或获取该文件时使用。")
    public String downloadFile(
            @ToolParam(description = "资源的完整 URL，必须以 http:// 或 https:// 开头") String url,
            @ToolParam(description = "保存到本地的文件名，例如 report.pdf") String fileName) {

        // 1. 参数校验：URL 为空或协议不支持时直接返回可读原因，让模型自行纠正
        if (url == null || url.isBlank()) {
            return "下载失败：URL 不能为空。";
        }
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return "下载失败：只支持 http/https 协议，当前地址：" + url;
        }
        if (isInternalAddress(lowerUrl)) {
            return "下载失败：不允许访问内网或本机地址。";
        }

        // 2. 文件名消毒：只保留最后一段，去掉路径分隔符，防止路径穿越
        String safeName = sanitizeFileName(fileName, url);
        if (safeName.isEmpty()) {
            return "下载失败：文件名不合法。";
        }

        Path targetFile = Paths.get(DOWNLOAD_DIR, safeName);
        try {
            // 3. 确保目录存在，否则写文件会失败
            Files.createDirectories(Paths.get(DOWNLOAD_DIR));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    // 带上 UA：部分站点对没有 User-Agent 的请求直接返回 403
                    .header("User-Agent", "Mozilla/5.0 (compatible; SuperAiAgent/1.0)")
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // 4. 先看状态码：非 2xx 说明没有拿到资源，此时不能落盘
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                // 错误页内容一般很短，读一小段拼进提示，方便模型判断原因
                String errorBody = readLimited(response.body(), 200);
                return "下载失败：HTTP 状态码 " + statusCode + "，" + errorBody;
            }

            // 5. 响应头里声明的大小超限时直接拒绝，省得白下载
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (declaredLength > MAX_SIZE_BYTES) {
                return "下载失败：文件大小 " + declaredLength + " 字节，超过上限 "
                        + MAX_SIZE_BYTES + " 字节。";
            }

            // 6. 边读边写边计数：既能应对没有 Content-Length 的情况，也能及时中断超大文件
            long written = copyWithLimit(response.body(), targetFile);
            if (written > MAX_SIZE_BYTES) {
                Files.deleteIfExists(targetFile);   // 删掉半成品，避免留下损坏文件
                return "下载失败：文件超过大小上限 " + MAX_SIZE_BYTES + " 字节，已取消。";
            }

            return "下载成功：文件已保存到 " + targetFile.toAbsolutePath()
                    + "，大小 " + written + " 字节（约 " + (written / 1024) + " KB）。";

        } catch (IOException e) {
            // 网络不通、地址格式错误、磁盘写入失败等都会走到这里
            return "下载失败：" + e.getMessage();
        } catch (InterruptedException e) {
            // 线程被中断：恢复中断标记，避免吞掉中断信号
            Thread.currentThread().interrupt();
            return "下载被中断：" + url;
        }
    }

    /**
     * 把输入流写入目标文件，边写边统计字节数，超过 {@link #MAX_SIZE_BYTES} 立即停止。
     * <p>
     * 不用 {@code Files.copy} 是因为它无法中途判断大小：一旦文件超限，
     * 磁盘上已经留下一个巨大的半成品，只能事后删除。
     *
     * @param inputStream 响应体输入流（本方法负责关闭）
     * @param targetFile  目标文件（已存在则覆盖）
     * @return 实际读到的字节数；超过上限时返回的值会大于上限，由调用方判断并清理文件
     */
    private long copyWithLimit(InputStream inputStream, Path targetFile) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream in = inputStream;
             OutputStream out = Files.newOutputStream(targetFile,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SIZE_BYTES) {
                    // 超限：直接跳出，由调用方删除这个不完整的文件
                    break;
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    /**
     * 读取输入流的前若干个字符，用于把错误响应拼进提示信息。
     *
     * @param inputStream 响应体
     * @param maxChars    最多读取的字符数
     * @return 截取后的文本；读取失败返回空串
     */
    private String readLimited(InputStream inputStream, int maxChars) {
        try (InputStream in = inputStream) {
            byte[] bytes = in.readNBytes(maxChars);
            return new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 清洗文件名：去掉目录部分和非法字符，避免写到下载目录之外。
     *
     * @param fileName 模型传入的文件名，可能为 null
     * @param url      资源地址，文件名缺失时用它推导
     * @return 安全的文件名，无法推导时返回 "download_时间戳"
     */
    private String sanitizeFileName(String fileName, String url) {
        String name = fileName;
        if (name == null || name.isBlank()) {
            // 没给文件名就从 URL 路径推断，例如 .../a/b/report.pdf → report.pdf
            String path = URI.create(url).getPath();
            name = path.substring(path.lastIndexOf('/') + 1);
        }
        // 去掉 Windows/Linux 的路径分隔符，只留最后一段
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        // 替换文件系统不允许的字符（Windows 下 : * ? " < > | 都是非法的）
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isEmpty()) {
            name = "download_" + System.currentTimeMillis();
        }
        return name;
    }

    /**
     * 简单的内网地址判断：拦截本机与常见私有网段，降低 SSRF 风险。
     * <p>
     * 这是"够用就好"的实现，域名解析到内网 IP 的情况拦不住，生产环境需要出口白名单或代理层控制。
     *
     * @param lowerUrl 已转小写的 URL
     * @return true 表示是内网/本机地址，应当拒绝
     */
    private boolean isInternalAddress(String lowerUrl) {
        return lowerUrl.contains("//localhost")
                || lowerUrl.contains("//127.")
                || lowerUrl.contains("//0.0.0.0")
                || lowerUrl.contains("//10.")
                || lowerUrl.contains("//192.168.")
                || lowerUrl.matches(".*//172\\.(1[6-9]|2\\d|3[01])\\..*");
    }
}
