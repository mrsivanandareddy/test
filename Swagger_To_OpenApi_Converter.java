import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.output.MarkdownRender;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class SwaggerEndpointExtractor {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: SwaggerEndpointExtractor <input-swagger-file> <http-method> <path> [output-file]");
            System.out.println("Example: SwaggerEndpointExtractor swagger.yaml GET /api/users extracted_endpoint.yaml");
            return;
        }

        String inputFile = args[0];
        String httpMethod = args[1].toUpperCase();
        String path = args[2];
        String outputFile = args.length > 3 ? args[3] : "extracted_endpoint_openapi.yaml";

        try {
            // Step 1: Parse and resolve the original Swagger 2.0 file
            OpenAPI fullApi = parseAndResolveSwagger(inputFile);

            // Step 2: Extract the specific endpoint
            OpenAPI extractedApi = extractEndpoint(fullApi, httpMethod, path);

            // Step 3: Write the extracted endpoint to file
            writeOpenApiToFile(extractedApi, outputFile);

            System.out.println("Successfully extracted endpoint to: " + outputFile);
        } catch (Exception e) {
            System.err.println("Error processing Swagger file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static OpenAPI parseAndResolveSwagger(String inputFile) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true); // Resolve all $refs
        options.setResolveFully(true); // Fully resolve (including remote refs)

        SwaggerParseResult result = new OpenAPIParser().readLocation(inputFile, null, options);
        
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            System.out.println("Parser messages:");
            result.getMessages().forEach(System.out::println);
        }

        if (result.getOpenAPI() == null) {
            throw new RuntimeException("Failed to parse the input file");
        }

        return result.getOpenAPI();
    }

    private static OpenAPI extractEndpoint(OpenAPI fullApi, String httpMethod, String path) {
        OpenAPI extractedApi = new OpenAPI();
        
        // Copy basic info
        extractedApi.setOpenapi("3.0.1");
        extractedApi.setInfo(fullApi.getInfo());
        extractedApi.setServers(fullApi.getServers());
        extractedApi.setComponents(fullApi.getComponents());
        extractedApi.setTags(fullApi.getTags());
        extractedApi.setExternalDocs(fullApi.getExternalDocs());

        // Find and extract the specific path
        Paths paths = fullApi.getPaths();
        PathItem pathItem = paths.get(path);

        if (pathItem == null) {
            throw new RuntimeException("Path not found: " + path);
        }

        Operation operation = getOperation(pathItem, httpMethod);
        if (operation == null) {
            throw new RuntimeException("HTTP method " + httpMethod + " not found for path: " + path);
        }

        // Create new Paths with just this endpoint
        Paths extractedPaths = new Paths();
        PathItem extractedPathItem = new PathItem();

        // Set the specific operation based on HTTP method
        setOperation(extractedPathItem, httpMethod, operation);

        // Copy path-level parameters
        if (pathItem.getParameters() != null) {
            extractedPathItem.setParameters(pathItem.getParameters());
        }

        extractedPaths.addPathItem(path, extractedPathItem);
        extractedApi.setPaths(extractedPaths);

        return extractedApi;
    }

    private static Operation getOperation(PathItem pathItem, String httpMethod) {
        switch (httpMethod) {
            case "GET": return pathItem.getGet();
            case "POST": return pathItem.getPost();
            case "PUT": return pathItem.getPut();
            case "DELETE": return pathItem.getDelete();
            case "PATCH": return pathItem.getPatch();
            case "HEAD": return pathItem.getHead();
            case "OPTIONS": return pathItem.getOptions();
            case "TRACE": return pathItem.getTrace();
            default: throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        }
    }

    private static void setOperation(PathItem pathItem, String httpMethod, Operation operation) {
        switch (httpMethod) {
            case "GET": pathItem.setGet(operation); break;
            case "POST": pathItem.setPost(operation); break;
            case "PUT": pathItem.setPut(operation); break;
            case "DELETE": pathItem.setDelete(operation); break;
            case "PATCH": pathItem.setPatch(operation); break;
            case "HEAD": pathItem.setHead(operation); break;
            case "OPTIONS": pathItem.setOptions(operation); break;
            case "TRACE": pathItem.setTrace(operation); break;
            default: throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        }
    }

    private static void writeOpenApiToFile(OpenAPI openAPI, String outputFile) throws IOException {
        ObjectMapper mapper;
        
        if (outputFile.endsWith(".json")) {
            mapper = Json.mapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFile), openAPI);
        } else {
            mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
            mapper.writeValue(new File(outputFile), openAPI);
        }
    }
}