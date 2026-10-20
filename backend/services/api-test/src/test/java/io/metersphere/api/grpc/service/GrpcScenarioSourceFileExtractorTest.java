package io.metersphere.api.grpc.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcScenarioSourceFileExtractorTest {

    private final GrpcScenarioSourceFileExtractor extractor = new GrpcScenarioSourceFileExtractor();

    @Test
    void extractsTextDocxAndXlsxWithoutOfficeDependencies() throws Exception {
        MockMultipartFile text = new MockMultipartFile(
                "files", "cases.md", "text/markdown", "# 原始用例".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile docx = new MockMultipartFile(
                "files", "cases.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zip(Map.of("word/document.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <w:document xmlns:w="urn:w"><w:body><w:p><w:r><w:t>Word 用例</w:t></w:r></w:p></w:body></w:document>
                        """)));
        MockMultipartFile xlsx = new MockMultipartFile(
                "files", "cases.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                zip(Map.of(
                        "xl/sharedStrings.xml", """
                                <?xml version="1.0" encoding="UTF-8"?>
                                <sst xmlns="urn:x"><si><t>Excel 用例</t></si></sst>
                                """,
                        "xl/worksheets/sheet1.xml", """
                                <?xml version="1.0" encoding="UTF-8"?>
                                <worksheet xmlns="urn:x"><sheetData><row><c t="s"><v>0</v></c><c><v>200</v></c></row></sheetData></worksheet>
                                """)));

        GrpcScenarioSourceFileExtractor.ExtractionResult result = extractor.extract(
                new MockMultipartFile[]{text, docx, xlsx});

        assertTrue(result.text().contains("原始用例"));
        assertTrue(result.text().contains("Word 用例"));
        assertTrue(result.text().contains("Excel 用例\t200"));
        assertTrue(result.images().isEmpty());
    }

    @Test
    void keepsImagesForVisionModel() {
        MockMultipartFile image = new MockMultipartFile(
                "files", "case.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        GrpcScenarioSourceFileExtractor.ExtractionResult result = extractor.extract(
                new MockMultipartFile[]{image});

        assertEquals(1, result.images().size());
        assertTrue(result.text().isEmpty());
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
