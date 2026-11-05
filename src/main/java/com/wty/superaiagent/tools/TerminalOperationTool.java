package com.wty.superaiagent.tools;

import com.wty.superaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 20 终端操作工具

 * <pre>
 *   dir / ls            查看当前目录
 *   type pom.xml        查看文件内容（Windows）
 *   java -version       查看 JDK 版本
 * </pre>
 * 执行流程与 {@link FileOperationTool} 一样：模型根据 {@code description} 决定调用，
 * 传入一条命令字符串，本类执行后把"退出码 + 输出"返回给模型。
 * <p>
 * 二、实现要点
 * <ul>
 *     <li><b>为什么用 {@link ProcessBuilder} 而不是 {@code Runtime.exec}</b>：
 *     ProcessBuilder 能指定工作目录、能合并错误流、参数以列表形式传入，可控性更好。</li>
 *     <li><b>为什么要包一层 {@code cmd.exe /c}（Linux 上是 {@code sh -c}）</b>：
 *     直接用 ProcessBuilder 只能执行一个可执行文件，不支持管道、重定向、通配符等 shell 语法。
 *     套一层 shell 才能执行 {@code dir /s | find "abc"} 这种复合命令。</li>
 *     <li><b>为什么要在单独线程里读输出</b>：进程的标准输出是管道，缓冲区（通常 64KB）写满后
 *     子进程会阻塞。如果在主线程里先 {@code waitFor()} 再读，双方互相等待就会死锁。
 *     所以一边等进程结束、一边由另一个线程把输出读完。</li>
 *     <li><b>为什么按 {@code native.encoding} 解码</b>：中文 Windows 的 cmd 输出是 GBK 编码，
 *     而 Java 18 之后 JVM 默认字符集是 UTF-8，直接用 UTF-8 解码会把中文变成乱码。
 *     {@code native.encoding} 是 JDK 提供的"操作系统本地编码"。</li>
 * </ul>
 * <p>
 * 三、安全说明（这是整个项目里风险最高的工具）
 * 这个工具相当于把 shell 交给了大模型。如果用户输入被恶意提示词注入，模型可能执行删除文件、
 * 上传数据之类的命令。因此代码里做了三层限制：
 * <ol>
 *     <li>危险命令黑名单拦截（只是兜底，绕过方式很多，不能当作安全边界）；</li>
 *     <li>固定工作目录为 {@code tmp}，命令默认在这里执行，降低误删项目文件的风险；</li>
 *     <li>超时强制终止，避免 {@code ping} 这类命令把对话卡死。</li>
 * </ol>
 * 真正上线时必须改用"命令白名单"，或者把执行环境放进容器/沙箱里隔离。
 * <p>
 * 四、使用注意
 * <ul>
 *     <li>每次调用都是<b>一个新进程</b>，{@code cd} 只在本次调用内有效，不会影响下一次；</li>
 *     <li>只捕获标准输出和标准错误（已合并），交互式命令（如 {@code pause}、需要输入密码的命令）会卡到超时；</li>
 *     <li>输出会被截断，避免超长内容撑爆模型上下文。</li>
 * </ul>
 *
 * @author 王天一
 * @version 1.0
 */
public class TerminalOperationTool {

    /**
     * 命令执行超时时间（秒）：超时后强制销毁进程
     */
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * 返回给模型的最大输出字符数：超出部分截断，防止撑爆上下文
     */
    private static final int MAX_OUTPUT_LENGTH = 4000;

    /**
     * 当前是否是 Windows 系统：决定包 {@code cmd.exe /c} 还是 {@code sh -c}
     */
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    /**
     * 危险命令黑名单（小写匹配）
     * <p>
     * 注意：这是"防手滑"级的兜底，不是安全边界。命令可以用变量、编码等方式绕过，
     * 生产环境请改成白名单。
     */
    private static final List<String> DANGEROUS_PATTERNS = List.of(
            "rm -rf /", "rm -rf ~", "rm -rf *", "mkfs", "format ",
            "shutdown", "reboot", "init 0",
            "del /f /s /q c:\\", "rd /s /q c:\\", "format c:",
            "chmod -r 777 /", "> /dev/sda", ":(){"
    );

    /**
     * 执行一条终端命令（供大模型调用）。
     * <p>
     * description 需要写清"什么时候用"，模型才会在需要查看目录、运行命令时调用它。
     *
     * @param command 要执行的命令，可以包含管道、重定向等 shell 语法
     * @return "退出码 + 输出"的文本；命令为空、被拦截、超时或执行失败时返回对应的说明文字
     */
    @Tool(description = "执行一条终端命令并返回输出结果。适用于查看目录内容、查看文件、查询系统版本、"
            + "运行脚本等需要与操作系统交互的场景。命令在项目根目录下的 tmp 目录中执行，超时时间为 30 秒。")
    public String executeCommand(@ToolParam(description = "要执行的终端命令，例如 dir、ls -l、java -version")
                                 String command) {

        // 1. 基本校验：模型可能传空串，直接告诉它而不是让 shell 报错
        if (command == null || command.isBlank()) {
            return "执行失败：命令不能为空。";
        }

        // 2. 危险命令拦截（转小写后做包含匹配，避免大小写绕过）
        String lowerCommand = command.toLowerCase(Locale.ROOT);
        for (String pattern : DANGEROUS_PATTERNS) {
            if (lowerCommand.contains(pattern)) {
                return "出于安全考虑，该命令被拒绝执行：" + command;
            }
        }

        // 3. 准备并创建工作目录：所有命令都在这里执行，避免误删项目源码
        File workDir = new File(FileConstant.FILE_SAVE_PATH);
        if (!workDir.exists() && !workDir.mkdirs()) {
            return "执行失败：无法创建命令执行目录 " + workDir.getAbsolutePath();
        }

        // 4. 组装命令行：套一层 shell 才能支持管道、重定向等语法
        List<String> commandLine = IS_WINDOWS
                ? List.of("cmd.exe", "/c", command)
                : List.of("sh", "-c", command);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.directory(workDir);   // 指定工作目录
        processBuilder.redirectErrorStream(true);   // 把标准错误合并进标准输出，方便一起返回给模型

        Process process = null;
        ExecutorService outputReader = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "terminal-output-reader");
            t.setDaemon(true);   // 守护线程：即使读取没结束也不阻止 JVM 退出
            return t;
        });

        try {
            process = processBuilder.start();

            // 在单独线程里读输出：否则输出超过管道缓冲区时子进程会阻塞，主线程等它结束就死锁
            Process finalProcess = process;
            Future<String> outputFuture = outputReader.submit(() -> readAll(finalProcess.getInputStream()));

            // 等待进程结束，最多等 TIMEOUT_SECONDS 秒
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "命令执行超时（超过 " + TIMEOUT_SECONDS + " 秒），已强制终止：" + command;
            }

            // 进程结束后拿到退出码和输出。退出码 0 表示成功，非 0 表示失败（具体含义由命令自己决定）
            int exitCode = process.exitValue();
            String output = outputFuture.get(2, TimeUnit.SECONDS);

            // 5. 输出截断：模型上下文有限，超长内容只保留前面一部分
            if (output.length() > MAX_OUTPUT_LENGTH) {
                output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n...[输出过长，已截断]";
            }
            if (output.isBlank()) {
                output = "(命令执行成功，但没有输出)";
            }

            return "退出码：" + exitCode + "\n输出：\n" + output;

        } catch (IOException e) {
            // 启动失败（找不到 cmd.exe / sh）或读写管道出错
            return "命令执行失败：" + e.getMessage();
        } catch (InterruptedException e) {
            // 线程被中断：恢复中断标记，避免吞掉中断信号
            Thread.currentThread().interrupt();
            return "命令执行被中断：" + command;
        } catch (ExecutionException | TimeoutException e) {
            // 读取输出的线程出错，或进程结束后仍拿不到输出
            return "读取命令输出失败：" + e.getMessage();
        } finally {
            // 收尾：确保读取线程关闭、进程被清理，避免线程和进程泄漏
            outputReader.shutdownNow();
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 把输入流全部读成字符串。
     * <p>
     * 之所以要指定字符集：中文 Windows 下 {@code cmd.exe} 输出 GBK 字节，
     * 而 Java 18+ 的默认字符集是 UTF-8，不指定就会出现乱码。
     *
     * @param inputStream 进程的标准输出（已合并标准错误）
     * @return 解码后的文本
     */
    private String readAll(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        return new String(bytes, consoleCharset());
    }

    /**
     * 获取操作系统本地编码（如中文 Windows 的 GBK），取不到时退回 JVM 默认字符集。
     *
     * @return 用于解码命令输出的字符集
     */
    private static Charset consoleCharset() {
        String nativeEncoding = System.getProperty("native.encoding");
        try {
            return nativeEncoding != null ? Charset.forName(nativeEncoding) : Charset.defaultCharset();
        } catch (Exception e) {
            // 该编码名不被当前 JVM 支持时，退回默认字符集
            return Charset.defaultCharset();
        }
    }
}
