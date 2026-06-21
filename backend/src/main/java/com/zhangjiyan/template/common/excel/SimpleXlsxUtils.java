package com.zhangjiyan.template.common.excel;

import com.zhangjiyan.template.common.exception.BusinessException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SimpleXlsxUtils {

    private SimpleXlsxUtils() {
    }

    static byte[] writeWorkbook(String sheetName, List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """.formatted(xml(sheetName)));
            put(zip, "xl/worksheets/sheet1.xml", worksheet(headers, rows));
            zip.finish();
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Excel 文件生成失败");
        }
    }

    public static List<List<String>> readWorkbook(InputStream inputStream) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("Excel 文件读取失败");
        }
        byte[] sheetBytes = entries.get("xl/worksheets/sheet1.xml");
        if (sheetBytes == null) {
            throw new BusinessException("Excel 文件缺少 sheet1");
        }
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        return parseSheet(sheetBytes, sharedStrings);
    }

    private static String worksheet(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        builder.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        appendRow(builder, 1, headers);
        for (int i = 0; i < rows.size(); i++) {
            appendRow(builder, i + 2, rows.get(i));
        }
        builder.append("</sheetData></worksheet>");
        return builder.toString();
    }

    private static void appendRow(StringBuilder builder, int rowIndex, List<String> values) {
        builder.append("<row r=\"").append(rowIndex).append("\">");
        for (int i = 0; i < values.size(); i++) {
            builder.append("<c r=\"").append(columnName(i)).append(rowIndex).append("\" t=\"inlineStr\"><is><t>")
                    .append(xml(values.get(i)))
                    .append("</t></is></c>");
        }
        builder.append("</row>");
    }

    private static List<String> parseSharedStrings(byte[] bytes) {
        if (bytes == null) {
            return List.of();
        }
        Document document = parseXml(bytes);
        NodeList nodes = document.getElementsByTagNameNS("*", "t");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent());
        }
        return values;
    }

    private static List<List<String>> parseSheet(byte[] bytes, List<String> sharedStrings) {
        Document document = parseXml(bytes);
        NodeList rowNodes = document.getElementsByTagNameNS("*", "row");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            NodeList cellNodes = row.getElementsByTagNameNS("*", "c");
            List<String> values = new ArrayList<>();
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element cell = (Element) cellNodes.item(j);
                int columnIndex = columnIndex(cell.getAttribute("r"));
                while (values.size() <= columnIndex) {
                    values.add("");
                }
                values.set(columnIndex, cellText(cell, sharedStrings));
            }
            rows.add(values);
        }
        return rows;
    }

    private static String cellText(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        NodeList inlineTexts = cell.getElementsByTagNameNS("*", "t");
        if ("inlineStr".equals(type) && inlineTexts.getLength() > 0) {
            return inlineTexts.item(0).getTextContent();
        }
        NodeList values = cell.getElementsByTagNameNS("*", "v");
        if (values.getLength() == 0) {
            return "";
        }
        String value = values.item(0).getTextContent();
        if ("s".equals(type)) {
            int index = Integer.parseInt(value);
            return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
        }
        return value;
    }

    private static Document parseXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            throw new BusinessException("Excel XML 解析失败");
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String columnName(int index) {
        StringBuilder builder = new StringBuilder();
        int current = index;
        do {
            builder.insert(0, (char) ('A' + current % 26));
            current = current / 26 - 1;
        } while (current >= 0);
        return builder.toString();
    }

    private static int columnIndex(String cellRef) {
        int result = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = cellRef.charAt(i);
            if (!Character.isLetter(ch)) {
                break;
            }
            result = result * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return Math.max(result - 1, 0);
    }

    private static String xml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
