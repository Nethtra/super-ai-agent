package com.wty.superaiagent.tools;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.wty.superaiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 22 PDF 生成工具

 * <p>
 * 二、为什么用 iText 7
 * JDK 没有生成 PDF 的能力，需要第三方库。iText 7 的模块划分是：
 * <ul>
 *     <li>{@code com.itextpdf:kernel}：PDF 底层对象、页面、字体，{@link PdfDocument}、{@link PdfWriter} 在这里；</li>
 *     <li>{@code com.itextpdf:layout}：高层排版 API，{@link Document}、{@link Paragraph} 在这里，
 *     它会自动处理换行、分页，不用自己算坐标；</li>
 *     <li>{@code com.itextpdf:itext-asian}：<b>中文字体包</b>。iText 7 本体只内置西文字体，
 *     要输出中文必须引入它，否则创建字体时直接抛异常。</li>
 * </ul>
 * <p>
 * 三、中文字体的关键点
 * <pre>
 *   PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
 * </pre>
 * 第一个参数是字体名（宋体的一种，由 itext-asian 提供），第二个参数是编码。
 * {@code UniGB-UCS2-H} 表示"简体中文 + UCS2 编码 + 横向书写"，
 * 中文 PDF 几乎都用这一组搭配。写完要 {@code document.setFont(font)}，
 * 否则段落用的还是默认西文字体，中文会显示成空白或方框。
 * <p>
 * 四、使用注意（踩坑点）
 * <ul>
 *     <li><b>必须 close 之后再读文件大小</b>：iText 是边写边缓冲的，
 *     {@code document.close()} 之前文件里可能只有几十字节的头部，
 *     {@code file.length()} 拿到的是不完整的大小。</li>
 *     <li><b>close 会级联关闭</b>：关闭 {@code Document} 会依次关闭 {@code PdfDocument} 和 {@code PdfWriter}，
 *     所以只需要关最外层，因此这里用 try-with-resources 管住 {@code Document} 即可。</li>
 *     <li><b>同名文件覆盖</b>：目标文件已存在时会被直接覆盖。</li>
 *     <li><b>License</b>：iText 7 是 AGPL 协议，学习和个人项目没问题，商业闭源项目需要购买授权。</li>
 *     <li><b>内容长度</b>：超长文本会自动分页，但一次传入几万字会明显变慢，
 *     需要大量内容时建议让模型分段写入。</li>
 * </ul>
 *
 * @author 王天一
 * @version 1.0
 */
@Slf4j
public class PDFGenerationTool {

    /**
     * 中文字体名（由依赖 {@code com.itextpdf:font-asian} 提供）
     */
    private static final String CJK_FONT = "STSong-Light";

    /**
     * 中文字体编码：简体中文 + UCS2 + 横向书写
     */
    private static final String CJK_ENCODING = "UniGB-UCS2-H";

    /**
     * 字体包不可用时的系统字体备选列表（先 Windows 常见的，再 Linux 常见的）
     * <p>
     * {@code .ttc} 是字体集合，需要用"路径,索引"的形式指定其中第几个字体，索引从 0 开始。
     */
    private static final List<String> SYSTEM_FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/msyh.ttc,0",      // 微软雅黑
            "C:/Windows/Fonts/simhei.ttf",      // 黑体
            "C:/Windows/Fonts/simsun.ttc,0",    // 宋体
            "C:/Windows/Fonts/simkai.ttf",      // 楷体
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0",   // Linux 文泉驿正黑
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0"
    );

    /**
     * 根据文本内容生成 PDF 文件（供大模型调用）。
     *
     * @return 成功时返回文件路径和大小；参数非法、目录创建失败或写入出错时返回失败原因
     */
    @Tool(description = "根据文本内容生成 PDF 文件并保存到本地。"
            + "当用户要求把内容导出为 PDF、生成报告或正式文档时使用。")
    public String generatePDF(
            @ToolParam(description = "PDF 文件名，例如 training-plan.pdf") String fileName,
            @ToolParam(description = "要写入 PDF 的文本内容，支持中文，可用换行分隔段落") String content) {

        // 1. 内容校验：空的 PDF 没有意义，直接告诉模型而不是生成一个空文件
        if (content == null || content.isBlank()) {
            return "生成失败：PDF 内容不能为空。";
        }

        // 2. 文件名消毒：去掉目录部分和非法字符，并补上 .pdf 后缀
        String safeName = sanitizeFileName(fileName);

        // 3. 确保输出目录存在：第一次运行时 tmp/pdf 还不存在，直接写文件会失败
        File outputDir = new File(FileConstant.FILE_SAVE_PATH);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            return "生成失败：无法创建输出目录 " + outputDir.getAbsolutePath();
        }

        File targetFile = new File(outputDir, safeName);

        // 4. try-with-resources 管住最外层 Document：它关闭时会级联关闭 PdfDocument 和 PdfWriter
        try (PdfWriter writer = new PdfWriter(targetFile);
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            // 指定中文字体：不设置的话中文无法渲染
            // 用 resolveChineseFont() 而不是直接 createFont：字体包缺失时会自动回退到系统字体
            document.setFont(resolveChineseFont());

            // 按行切成段落：Paragraph 之间自动换行，整页写满后 iText 会自动分页
            for (String line : content.split("\\R")) {
                document.add(new Paragraph(line));
            }
            // 离开 try 代码块时自动 close，缓冲内容才会真正刷到磁盘（见下面第 5 步）

        } catch (IOException e) {
            return "生成失败：" + e.getMessage();
        }

        // 5. 关闭之后读大小才准确：iText 在 close 前只写了文件头，length() 会偏小
        return "生成成功：PDF 已保存到 " + targetFile.getAbsolutePath()
                + "，大小 " + targetFile.length() + " 字节。";
    }

    /**
     * 取得一个能写中文的字体，优先用 iText 的字体包，失败时回退到系统字体。
     * <p>
     * 为什么要回退：{@code STSong-Light} 这类 CJK 字体名由 {@code com.itextpdf:font-asian} 提供，
     * 如果依赖没引入（或误引了 iText 5 的 {@code itext-asian}），
     * {@code createFont} 会抛 "Type of font STSong-Light is not recognized"。
     * 这时用系统里已有的中文字体文件同样能输出中文。
     *
     * @return 可用的中文字体
     * @throws IOException 字体包和系统字体都不可用时抛出，由调用方转成失败提示
     */
    private PdfFont resolveChineseFont() throws IOException {
        // 记录每个候选的失败原因：字体问题最难查，把原因带出去比只抛一句"失败"有用得多
        List<String> failures = new ArrayList<>();

        // 首选：iText 官方字体包，跨平台，不依赖本机装了什么字体
        try {
            PdfFont font = PdfFontFactory.createFont(CJK_FONT, CJK_ENCODING);
            log.debug("PDF 使用字体包字体：{}", CJK_FONT);
            return font;
        } catch (Exception e) {
            failures.add(CJK_FONT + " → " + e.getMessage());
            log.warn("字体包字体 {} 不可用：{}", CJK_FONT, e.getMessage());
        }

        // 次选：系统字体文件。IDENTITY_H 表示按 Unicode 编码，是中文字体的通用选择
        for (String candidate : SYSTEM_FONT_CANDIDATES) {
            String path = candidate.contains(",") ? candidate.substring(0, candidate.indexOf(',')) : candidate;
            if (!new File(path).exists()) {
                continue;   // 本机没有这个字体，跳过（不算失败，避免提示信息被噪声淹掉）
            }
            try {
                PdfFont font = PdfFontFactory.createFont(candidate, PdfEncodings.IDENTITY_H);
                log.debug("PDF 使用系统字体：{}", candidate);
                return font;
            } catch (Exception e) {
                failures.add(candidate + " → " + e.getMessage());
                log.warn("系统字体 {} 加载失败：{}", candidate, e.getMessage());
            }
        }

        // 都失败：把每个候选的失败原因一并返回，方便定位是缺依赖还是字体文件本身有问题
        throw new IOException("无法加载中文字体。请确认依赖 com.itextpdf:font-asian 已引入（版本需与 kernel 一致），"
                + "且本机存在中文字体文件。失败详情：" + String.join("；", failures));
    }

    /**
     * 清洗文件名：去掉目录部分、替换非法字符、补 .pdf 后缀。
     *
     * @param fileName 模型传入的文件名，可能为 null
     * @return 安全的 PDF 文件名，缺失时用时间戳兜底
     */
    private String sanitizeFileName(String fileName) {
        String name = fileName == null ? "" : fileName.trim();

        // 去掉 Windows/Linux 的路径分隔符，只留最后一段，防止写到目录之外
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);

        // 替换文件系统不允许的字符（Windows 下 : * ? " < > | 非法）
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");

        if (name.isEmpty()) {
            name = "document_" + System.currentTimeMillis();
        }
        // 统一补后缀，避免生成一个没有扩展名、系统打不开的文件
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name;
    }
}
