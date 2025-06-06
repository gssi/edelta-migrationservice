package it.gssi.edelta.migrationservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.StreamUtils;

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
	void setUp() throws IOException {
		// Setup the model folder for tests
		properties.setModelfolder(tempDir.toString());
	}

	@Test
	public void testPersonlistMigration() throws Exception {
		// Load input XML files from resources
		ClassPathResource inputResource1 = new ClassPathResource("input-models/personlist/My.persons");
		inputXml1 = new String(StreamUtils.copyToByteArray(inputResource1.getInputStream()), StandardCharsets.UTF_8);
		
		ClassPathResource inputResource2 = new ClassPathResource("input-models/personlist/My2.persons");
		inputXml2 = new String(StreamUtils.copyToByteArray(inputResource2.getInputStream()), StandardCharsets.UTF_8);
		
		// Load expected output XML files from resources
		ClassPathResource expectedResource1 = new ClassPathResource("expectations/personlist/My.persons");
		expectedOutputXml1 = new String(StreamUtils.copyToByteArray(expectedResource1.getInputStream()), StandardCharsets.UTF_8);
		
		ClassPathResource expectedResource2 = new ClassPathResource("expectations/personlist/My2.persons");
		expectedOutputXml2 = new String(StreamUtils.copyToByteArray(expectedResource2.getInputStream()), StandardCharsets.UTF_8);

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
		Map<String, String> extractedContents = extractZipContents(responseBytes);
		
		// Expected file names
		String file1Name = "My.persons";
		String file2Name = "My2.persons";

		// Verify the model files are present in the ZIP
		assertNotNull(extractedContents.get(file1Name), file1Name + " not found in the ZIP file");
		assertNotNull(extractedContents.get(file2Name), file2Name + " not found in the ZIP file");

		// Compare the extracted content with expected output using normalized line endings
		assertEquals(normalizeLineEndings(expectedOutputXml1), normalizeLineEndings(extractedContents.get(file1Name)));
		assertEquals(normalizeLineEndings(expectedOutputXml2), normalizeLineEndings(extractedContents.get(file2Name)));
	}

	@Test
	public void testLibraryMigration() throws Exception {
		// File names for input and output
		String[] fileNames = {
			"MyBookDatabase1.books",
			"MyBookDatabase2.books",
			"MyLibrary1.library",
			"MyLibrary2.library"
		};
		
		// Maps for input and expected content
		Map<String, String> inputFiles = new HashMap<>();
		Map<String, String> expectedOutputFiles = new HashMap<>();
		
		// Load input files and expected output files
		for (String fileName : fileNames) {
			inputFiles.put(fileName, loadResource("input-models/library/" + fileName));
			expectedOutputFiles.put(fileName, loadResource("expectations/library/" + fileName));
		}
		
		// Create mock multipart files
		List<MockMultipartFile> mockFiles = new ArrayList<>();
		for (String fileName : fileNames) {
			mockFiles.add(new MockMultipartFile(
				"modelFiles", 
				fileName, 
				MediaType.TEXT_XML_VALUE,
				inputFiles.get(fileName).getBytes(StandardCharsets.UTF_8)
			));
		}
		
		// Perform the request with all files
		MvcResult result = mockMvc.perform(multipart("/api/v1/migrationservice/")
				.file(mockFiles.get(0))
				.file(mockFiles.get(1))
				.file(mockFiles.get(2))
				.file(mockFiles.get(3)))
				.andExpect(status().isOk()).andReturn();

		// Get the response as byte array
		byte[] responseBytes = result.getResponse().getContentAsByteArray();

		// Verify the response
		assertNotNull(responseBytes);

		// Extract and verify the contents of the zip file
		Map<String, String> extractedContents = extractZipContents(responseBytes);
		
		// Verify each expected file
		for (String fileName : fileNames) {
			// Verify the file is present in the ZIP
			assertNotNull(extractedContents.get(fileName), fileName + " not found in the ZIP file");
			
			// Compare the extracted content with expected output using normalized line endings
			assertEquals(
				normalizeLineEndings(expectedOutputFiles.get(fileName)), 
				normalizeLineEndings(extractedContents.get(fileName)),
				"Content mismatch for file: " + fileName
			);
		}
	}
	
	@Test
	public void testCombinedMigration() throws Exception {
		// File names for input and output - combining both personlist and library files
		String[] personlistFiles = {"My.persons", "My2.persons"};
		String[] libraryFiles = {
			"MyBookDatabase1.books",
			"MyBookDatabase2.books",
			"MyLibrary1.library",
			"MyLibrary2.library"
		};
		
		// Load all input files and their expected outputs
		Map<String, String> inputFiles = new HashMap<>();
		Map<String, String> expectedOutputFiles = new HashMap<>();
		
		// Load personlist files
		for (String fileName : personlistFiles) {
			inputFiles.put(fileName, loadResource("input-models/personlist/" + fileName));
			expectedOutputFiles.put(fileName, loadResource("expectations/personlist/" + fileName));
		}
		
		// Load library files
		for (String fileName : libraryFiles) {
			inputFiles.put(fileName, loadResource("input-models/library/" + fileName));
			expectedOutputFiles.put(fileName, loadResource("expectations/library/" + fileName));
		}
		
		// Create mock multipart files for all models
		List<MockMultipartFile> mockFiles = new ArrayList<>();
		for (Map.Entry<String, String> entry : inputFiles.entrySet()) {
			mockFiles.add(new MockMultipartFile(
				"modelFiles", 
				entry.getKey(), 
				MediaType.TEXT_XML_VALUE,
				entry.getValue().getBytes(StandardCharsets.UTF_8)
			));
		}
		
		// Build the multipart request using method chaining
		var requestBuilder = multipart("/api/v1/migrationservice/");
		
		// We need to explicitly add each file
		for (int i = 0; i < mockFiles.size(); i++) {
			if (i == 0) {
				// First file is added directly 
				requestBuilder = requestBuilder.file(mockFiles.get(0));
			} else {
				// Subsequent files are added using method chaining
				requestBuilder = requestBuilder.file(mockFiles.get(i));
			}
		}
		
		// Perform the request with all files
		MvcResult result = mockMvc.perform(requestBuilder)
				.andExpect(status().isOk()).andReturn();

		// Get the response as byte array
		byte[] responseBytes = result.getResponse().getContentAsByteArray();

		// Verify the response
		assertNotNull(responseBytes);

		// Extract and verify the contents of the zip file
		Map<String, String> extractedContents = extractZipContents(responseBytes);
		
		// Combine all filenames for verification
		List<String> allFileNames = new ArrayList<>();
		allFileNames.addAll(Arrays.asList(personlistFiles));
		allFileNames.addAll(Arrays.asList(libraryFiles));
		
		// Verify each expected file
		for (String fileName : allFileNames) {
			// Verify the file is present in the ZIP
			assertNotNull(extractedContents.get(fileName), fileName + " not found in the ZIP file");
			
			// Compare the extracted content with expected output using normalized line endings
			assertEquals(
				normalizeLineEndings(expectedOutputFiles.get(fileName)), 
				normalizeLineEndings(extractedContents.get(fileName)),
				"Content mismatch for file: " + fileName
			);
		}
	}
	
	/**
	 * Helper method to normalize line endings to ensure consistent comparison
	 * across different platforms.
	 */
	private String normalizeLineEndings(String text) {
		// Normalize line endings by removing carriage returns
		return text.replace("\r", "");
	}
	
	/**
	 * Helper method to extract the contents of files from a ZIP byte array.
	 * 
	 * @param zipBytes The ZIP file as a byte array
	 * @return A map where keys are filenames and values are file contents as strings
	 * @throws IOException If an I/O error occurs
	 */
	private Map<String, String> extractZipContents(byte[] zipBytes) throws IOException {
		Map<String, String> extractedContents = new HashMap<>();
		
		try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zipInputStream.getNextEntry()) != null) {
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				int len;
				while ((len = zipInputStream.read(buffer)) > 0) {
					outputStream.write(buffer, 0, len);
				}
				
				String content = outputStream.toString(StandardCharsets.UTF_8.name());
				extractedContents.put(entry.getName(), content);
				
				zipInputStream.closeEntry();
			}
		}
		
		return extractedContents;
	}
	
	/**
	 * Helper method to load a resource file as a string.
	 * 
	 * @param resourcePath The path to the resource
	 * @return The content of the resource as a string
	 * @throws IOException If an I/O error occurs
	 */
	private String loadResource(String resourcePath) throws IOException {
		ClassPathResource resource = new ClassPathResource(resourcePath);
		return new String(StreamUtils.copyToByteArray(resource.getInputStream()), StandardCharsets.UTF_8);
	}
}
