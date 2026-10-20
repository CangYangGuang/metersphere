package io.metersphere.api.grpc.service;

import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class GrpcScenarioSourceFileExtractor {

    private static final int MAX_FILE_COUNT = 10;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 200_000;
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "csv");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg");

    public ExtractionResult extract(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return new ExtractionResult("", List.of());
        }
        if (files.length > MAX_FILE_COUNT) {
            throw new MSException("一次最多上传 10 个测试用例文件");
        }

        StringBuilder text = new StringBuilder();
        List<MultipartFile> images = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new MSException("单个测试用例文件不能超过 10 MB");
            }
            String fileName = StringUtils.defaultString(file.getOriginalFilename(), "source");
            String extension = StringUtils.substringAfterLast(fileName, ".").toLowerCase(Locale.ROOT);
            if (IMAGE_EXTENSIONS.contains(extension)) {
                images.add(file);
                continue;
            }

            String extracted = switch (extension) {
                case "txt", "md", "csv" -> readUtf8(file);
                case "docx" -> extractDocx(file);
                case "xlsx" -> extractXlsx(file);
                default -> throw new MSException("不支持的测试用例文件格式：" + fileName);
            };
            if (StringUtils.isNotBlank(extracted)) {
                text.append("\n\n===== 文件：").append(fileName).append(" =====\n").append(extracted.trim());
            }
            if (text.length() > MAX_TEXT_CHARS) {
                throw new MSException("测试用例文本内容不能超过 200000 个字符");
            }
        }
        if (images.size() > 4) {
            throw new MSException("一次最多上传 4 张测试用例图片");
        }
        return new ExtractionResult(text.toString().trim(), images);
    }

    private String readUtf8(MultipartFile file) {
        try {
            String value = new String(file.getBytes(), StandardCharsets.UTF_8);
            return StringUtils.removeStart(value, "\uFEFF");
        } catch (IOException e) {
            throw new MSException("读取测试用例文件失败：" + file.getOriginalFilename(), e);
        }
    }

    private String extractDocx(MultipartFile file) {
        Map<String, byte[]> entries = unzip(file);
        byte[] documentXml = entries.get("word/document.xml");
        if (documentXml == null) {
            throw new MSException("DOCX 文件缺少 word/document.xml：" + file.getOriginalFilename());
        }
        Document document = parseXml(documentXml, file.getOriginalFilename());
        StringBuilder result = new StringBuilder();
        NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
        for (int index = 0; index < paragraphs.getLength(); index++) {
            NodeList nodes = ((Element) paragraphs.item(index)).getElementsByTagNameNS("*", "t");
            StringBuilder paragraph = new StringBuilder();
            for (int textIndex = 0; textIndex < nodes.getLength(); textIndex++) {
                paragraph.append(nodes.item(textIndex).getTextContent());
            }
            if (!paragraph.isEmpty()) {
                result.append(paragraph).append('\n');
            }
        }
        return result.toString();
    }

    private String extractXlsx(MultipartFile file) {
        Map<String, byte[]> entries = unzip(file);
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"), file.getOriginalFilename());
        StringBuilder result = new StringBuilder();
        entries.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("xl/worksheets/sheet") && entry.getKey().endsWith(".xml"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendWorksheet(result, entry.getKey(), entry.getValue(), sharedStrings,
                        file.getOriginalFilename()));
        if (result.isEmpty()) {
            throw new MSException("XLSX 文件中未找到工作表内容：" + file.getOriginalFilename());
        }
        return result.toString();
    }

    private List<String> parseSharedStrings(byte[] xml, String fileName) {
        if (xml == null) {
            return List.of();
        }
        Document document = parseXml(xml, fileName);
        NodeList items = document.getElementsByTagNameNS("*", "si");
        List<String> result = new ArrayList<>(items.getLength());
        for (int index = 0; index < items.getLength(); index++) {
            NodeList textNodes = ((Element) items.item(index)).getElementsByTagNameNS("*", "t");
            StringBuilder value = new StringBuilder();
            for (int textIndex = 0; textIndex < textNodes.getLength(); textIndex++) {
                value.append(textNodes.item(textIndex).getTextContent());
            }
            result.add(value.toString());
        }
        return result;
    }

    private void appendWorksheet(StringBuilder target, String sheetName, byte[] xml, List<String> sharedStrings,
                                 String fileName) {
        Document document = parseXml(xml, fileName);
        target.append("\n[").append(sheetName).append("]\n");
        NodeList rows = document.getElementsByTagNameNS("*", "row");
        for (int rowIndex = 0; rowIndex < rows.getLength(); rowIndex++) {
            NodeList cells = ((Element) rows.item(rowIndex)).getElementsByTagNameNS("*", "c");
            List<String> values = new ArrayList<>();
            for (int cellIndex = 0; cellIndex < cells.getLength(); cellIndex++) {
                Element cell = (Element) cells.item(cellIndex);
                String type = cell.getAttribute("t");
                String value = firstText(cell, "v");
                if ("inlineStr".equals(type)) {
                    value = firstText(cell, "t");
                } else if ("s".equals(type) && StringUtils.isNumeric(value)) {
                    int sharedIndex = Integer.parseInt(value);
                    value = sharedIndex >= 0 && sharedIndex < sharedStrings.size() ? sharedStrings.get(sharedIndex) : value;
                }
                values.add(StringUtils.defaultString(value));
            }
            target.append(String.join("\t", values)).append('\n');
        }
    }

    private String firstText(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private Map<String, byte[]> unzip(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), input.readAllBytes());
                }
            }
            return entries;
        } catch (IOException e) {
            throw new MSException("解析 Office 文件失败：" + file.getOriginalFilename(), e);
        }
    }

    private Document parseXml(byte[] xml, String fileName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            restrictExternalAccess(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
            restrictExternalAccess(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new MSException("解析 Office XML 失败：" + fileName, e);
        }
    }

    private void restrictExternalAccess(DocumentBuilderFactory factory, String property) {
        try {
            factory.setAttribute(property, "");
        } catch (IllegalArgumentException ignored) {
            // Older Xerces versions do not expose these JAXP properties; external entities remain disabled above.
        }
    }

    public record ExtractionResult(String text, List<MultipartFile> images) {
    }
}
