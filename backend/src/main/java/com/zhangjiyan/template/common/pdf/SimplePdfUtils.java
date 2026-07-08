package com.zhangjiyan.template.common.pdf;

import com.zhangjiyan.template.common.exception.BusinessException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SimplePdfUtils {

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int LEFT = 48;
    private static final int TOP = 792;
    private static final int LINE_HEIGHT = 18;
    private static final int MAX_LINE_CHARS = 36;
    private static final int MAX_LINES_PER_PAGE = 38;

    private SimplePdfUtils() {
    }

    public static byte[] writeDocument(String title, List<String> lines) {
        try {
            List<String> wrappedLines = wrappedLines(title, lines == null ? List.of() : lines);
            List<List<String>> pages = pages(wrappedLines);
            if (pages.isEmpty()) {
                pages = List.of(List.of(normalize(title)));
            }
            return writePdf(title, pages);
        } catch (Exception ex) {
            throw new BusinessException("PDF 文件生成失败");
        }
    }

    private static byte[] writePdf(String title, List<List<String>> pages) throws Exception {
        List<String> bodies = new ArrayList<>();
        bodies.add("<< /Type /Catalog /Pages 2 0 R >>");
        bodies.add(pagesObject(pages.size()));
        bodies.add("""
                << /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H /DescendantFonts [4 0 R] >>
                """);
        bodies.add("""
                << /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light /CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 2 >> /FontDescriptor 5 0 R /DW 1000 >>
                """);
        bodies.add("""
                << /Type /FontDescriptor /FontName /STSong-Light /Flags 6 /FontBBox [0 -200 1000 900] /ItalicAngle 0 /Ascent 880 /Descent -120 /CapHeight 880 /StemV 80 >>
                """);
        for (int i = 0; i < pages.size(); i++) {
            int pageId = pageObjectId(i);
            int contentId = contentObjectId(i);
            bodies.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %d %d] /Resources << /Font << /F1 3 0 R >> >> /Contents %d 0 R >>"
                    .formatted(PAGE_WIDTH, PAGE_HEIGHT, contentId));
            bodies.add(streamObject(pageContent(title, pages.get(i), i + 1, pages.size())));
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeAscii(output, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < bodies.size(); i++) {
            offsets.add(output.size());
            writeAscii(output, (i + 1) + " 0 obj\n");
            writeAscii(output, bodies.get(i).trim());
            writeAscii(output, "\nendobj\n");
        }
        int xrefOffset = output.size();
        writeAscii(output, "xref\n0 " + (bodies.size() + 1) + "\n");
        writeAscii(output, "0000000000 65535 f \n");
        for (Integer offset : offsets) {
            writeAscii(output, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        writeAscii(output, "trailer\n<< /Size " + (bodies.size() + 1) + " /Root 1 0 R >>\n");
        writeAscii(output, "startxref\n" + xrefOffset + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static String pagesObject(int pageCount) {
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(pageObjectId(i)).append(" 0 R ");
        }
        return "<< /Type /Pages /Count %d /Kids [%s] >>".formatted(pageCount, kids.toString().trim());
    }

    private static int pageObjectId(int pageIndex) {
        return 6 + pageIndex * 2;
    }

    private static int contentObjectId(int pageIndex) {
        return pageObjectId(pageIndex) + 1;
    }

    private static String streamObject(String content) {
        int length = content.getBytes(StandardCharsets.ISO_8859_1).length;
        return "<< /Length " + length + " >>\nstream\n" + content + "endstream";
    }

    private static String pageContent(String title, List<String> lines, int pageNumber, int pageCount) {
        StringBuilder content = new StringBuilder();
        int y = TOP;
        content.append(textLine(normalize(title), LEFT, y, 16));
        y -= LINE_HEIGHT + 8;
        for (String line : lines) {
            content.append(textLine(line, LEFT, y, 11));
            y -= LINE_HEIGHT;
        }
        content.append(textLine("Page " + pageNumber + " / " + pageCount, LEFT, 36, 9));
        return content.toString();
    }

    private static String textLine(String text, int x, int y, int size) {
        return "BT /F1 %d Tf %d %d Td <%s> Tj ET\n".formatted(size, x, y, utf16Hex(text));
    }

    private static List<String> wrappedLines(String title, List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.addAll(wrap(normalize(line)));
        }
        return result;
    }

    private static List<String> wrap(String value) {
        if (value.isBlank()) {
            return List.of("");
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(start + MAX_LINE_CHARS, value.length());
            parts.add(value.substring(start, end));
            start = end;
        }
        return parts;
    }

    private static List<List<String>> pages(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += MAX_LINES_PER_PAGE) {
            pages.add(lines.subList(i, Math.min(i + MAX_LINES_PER_PAGE, lines.size())));
        }
        return pages;
    }

    private static String normalize(String value) {
        return value == null ? "" : value
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .trim();
    }

    private static String utf16Hex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16BE);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format(Locale.ROOT, "%02X", b & 0xff));
        }
        return hex.toString();
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }
}
