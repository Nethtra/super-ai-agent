package com.wty.superaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import com.wty.superaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 18 文件操作工具类：定义"读文件""写文件"两个可被大模型调用的工具方法。
 * 使用hutool的FileUtil
 * <p>
 * 三、使用方式
 * 本类没有 {@code @Component}，不是由 Spring 容器管理的 Bean。常见用法是在构造 ChatClient 时注册：
 * <pre>
 *   ChatClient.builder(chatModel).defaultTools(new FileOperationTool()).build();
 *   或者每次请求时：chatClient.prompt().tools(new FileOperationTool())
 * </pre>
 * 如果希望全项目共用一个实例，也可以给它加上 {@code @Component} 再注入。
 *
 * @author 王天一
 * @version 1.0
 */
public class FileOperationTool {

    /**
     * {@code System.getProperty("user.dir")} 是 JVM 启动时的工作目录，
     * 在 IDEA 里跑通常是项目根目录；打成 jar 后取决于启动脚本所在位置。
     */
    private final String filePath = FileConstant.FILE_SAVE_PATH + "/files";


    // 读文件的方法
    @Tool(description = "read content from a file")
    public String readFile(@ToolParam(description = "the file name") String fileName) {
        // 拼出完整路径：目录 + 文件名（未做路径校验，见类注释中的"路径穿越风险"）
        String fileReadPath = filePath + "/" + fileName;
        try {
            // Hutool 工具方法：按 UTF-8 读取为字符串；文件不存在会抛 IORuntimeException
            return FileUtil.readUtf8String(fileReadPath);
        } catch (Exception e) {
            // 不向外抛异常，而是把失败信息当作"工具结果"交还给模型，让它有机会自行纠正
            return "error reading file:" + e.getMessage();
        }
    }


    // 写文件的方法
    @Tool(description = "write content to a file")
    public String writeFile(@ToolParam(description = "the file name") String fileName,
                            @ToolParam(description = "the file content") String content) {
        String fileWritePath = filePath + "/" + fileName;
        try {
            // 先确保目录存在：目录不存在时直接写文件会失败（tmp/files 可能第一次运行时还没有）
            FileUtil.mkdir(filePath);
            // 覆盖写入（不是追加）；需要追加可改用 FileUtil.appendUtf8String
            FileUtil.writeUtf8String(content, fileWritePath);
            return "file successfully written to:" + fileWritePath;
        } catch (Exception e) {
            return "error writing file:" + e.getMessage();
        }
    }
}
