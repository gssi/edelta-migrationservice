package it.gssi.edelta.migrationservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import it.gssi.edelta.migrationservice.configuration.MigrationConfigProperties;

@SpringBootTest
@AutoConfigureMockMvc
public class MigrationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MigrationConfigProperties properties;

	@TempDir
	Path tempDir;

	private String inputXml1;
	private String inputXml2;
	private String expectedOutputXml1;
	private String expectedOutputXml2;

	@BeforeEach
	void setUp() {
		// Setup the model folder for tests
		properties.setModelfolder(tempDir.toString());

		// Input XML for My.persons
		inputXml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<person:List
				    xmi:version="2.0"
				    xmlns:xmi="http://www.omg.org/XMI"
				    xmlns:person="http://edelta/PersonList/v1">
				  <members firstname="John"
				      lastname="Doe"/>
				  <members firstname="Jane"
				      lastname="Doe"
				      gender="FEMALE"/>
				</person:List>
				""";

		// Input XML for My2.persons
		inputXml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<person:List
				    xmi:version="2.0"
				    xmlns:xmi="http://www.omg.org/XMI"
				    xmlns:person="http://edelta/PersonList/v1">
				  <members firstname="John"
				      lastname="Doe"/>
				  <members firstname="Jane"
				      lastname="Doe"
				      gender="FEMALE"/>
				</person:List>
				""";

		// Expected output XML for My.persons
		expectedOutputXml1 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<person:List
				    xmi:version="2.0"
				    xmlns:xmi="http://www.omg.org/XMI"
				    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				    xmlns:person="http://edelta/PersonList/v4">
				  <members xsi:type="person:Male"
				      name="John Doe"/>
				  <members xsi:type="person:Female"
				      name="Jane Doe"/>
				</person:List>
				""";

		// Expected output XML for My2.persons
		expectedOutputXml2 = """
				<?xml version="1.0" encoding="UTF-8"?>
				<person:List
				    xmi:version="2.0"
				    xmlns:xmi="http://www.omg.org/XMI"
				    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				    xmlns:person="http://edelta/PersonList/v4">
				  <members xsi:type="person:Male"
				      name="John Doe"/>
				  <members xsi:type="person:Female"
				      name="Jane Doe"/>
				</person:List>
				""";
	}

	@Test
	public void testModelMigration() throws Exception {
		// Create mock multipart files
		MockMultipartFile file1 = new MockMultipartFile("modelFiles", "My.persons", MediaType.TEXT_XML_VALUE,
				inputXml1.getBytes(StandardCharsets.UTF_8));

		MockMultipartFile file2 = new MockMultipartFile("modelFiles", "My2.persons", MediaType.TEXT_XML_VALUE,
				inputXml2.getBytes(StandardCharsets.UTF_8));

		// Perform the multipart request and get the result
		MvcResult result = mockMvc.perform(multipart("/api/v1/migrationservice/").file(file1).file(file2))
				.andExpect(status().isOk()).andReturn();

		// Get the response as byte array
		byte[] responseBytes = result.getResponse().getContentAsByteArray();

		// Verify the response
		assertNotNull(responseBytes);

		// Extract and verify the contents of the zip file
		ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(responseBytes));
		ZipEntry entry;

		// Maps to store the extracted content
		String extractedContent1 = null;
		String extractedContent2 = null;

		while ((entry = zipInputStream.getNextEntry()) != null) {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int len;
			while ((len = zipInputStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, len);
			}

			String content = outputStream.toString(StandardCharsets.UTF_8.name());

			if (entry.getName().equals("My.persons")) {
				extractedContent1 = content;
			} else if (entry.getName().equals("My2.persons")) {
				extractedContent2 = content;
			}

			zipInputStream.closeEntry();
		}
		zipInputStream.close();

		// Verify the model files are present in the ZIP
		assertNotNull(extractedContent1, "My.persons not found in the ZIP file");
		assertNotNull(extractedContent2, "My2.persons not found in the ZIP file");

		// Compare the extracted content with expected output using normalized line endings
		assertEquals(normalizeLineEndings(expectedOutputXml1), normalizeLineEndings(extractedContent1));
		assertEquals(normalizeLineEndings(expectedOutputXml2), normalizeLineEndings(extractedContent2));
	}

	/**
	 * Helper method to normalize line endings to ensure consistent comparison
	 * across different platforms.
	 */
	private String normalizeLineEndings(String text) {
		// Normalize line endings by removing carriage returns
		return text.replace("\r", "");
	}
}
