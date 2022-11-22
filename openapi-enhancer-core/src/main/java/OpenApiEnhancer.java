import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.net.ssl.SSLContext;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OpenApiEnhancer {

    public static final String DATA_SUFFIX = "--data";
    public static final String ARRAY = "array";
    public static final String ITEMS = "items";
    public static final String STRING = "string";
    public static final String TITLE = "title";
    public static final String LANGCODE = "langcode";
    public static final Logger logger = Logger.getLogger("OpenApiEnhancer Core");

    private OpenApiEnhancer(){}

    static void processOpenApiSpec(String inputSpec, String outputSpec, String user, String password) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (inputSpec == null) {
            throw new NullPointerException("No inputSpec file was provided");
        }


        logger.log(Level.INFO, "Reading inputSpec from: {0}", inputSpec);
        InputStream is = inputSpec.startsWith("http") ? getInputStreamREST(inputSpec, user, password) : Files.newInputStream(Paths.get(inputSpec));

        if (is == null) {
            throw new NullPointerException("Cannot load inputSpec");
        }

        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);

        removeSecurityField(object);
        cleanPaths(object);
        logger.log(Level.INFO, "Updating parameter objects");
        updateParameters(object);
        logger.log(Level.INFO, "Deleting empty require fields");
        deleteEmptyRequiredFields(object);
        logger.log(Level.INFO, "Adding include and filterpath query params");
        addIncludeAndFilterQueryParam(object);
        logger.log(Level.INFO, "Correcting definitions");
        correctDefinitions(object.getJSONObject("definitions"));
        logger.log(Level.INFO, "Correcting Timestamp Parse Error");
        correctTimestamps(object);
        logger.log(Level.INFO, "Correcting langcode types");
        correctLangcode(object);
        logger.log(Level.INFO, "Correcting uri types");
        correctUriType(object);
        logger.log(Level.INFO, "Correcting custom breadcrumb schema");
        correctBreadcrumbType(object);
        logger.log(Level.INFO, "Add meta to image schema");
        addMetaToImageType(object);

        String jsonStr = object.toString(2)
                .replace("language_reference", STRING)
                .replace("\\/", "/")
                .replace("/properties/data", DATA_SUFFIX)
                .replace("\"type\": \"link_url\"", " \"type\": \"string\"");

        try (FileWriter myWriter = new FileWriter(outputSpec)){
            logger.log(Level.INFO, "Writing outputSpec to: {0}", outputSpec);
            myWriter.write(jsonStr);
        } catch (IOException e) {
            logger.log(Level.INFO, "Cannot write outputSpec.");
            e.printStackTrace();
        }

        logger.log(Level.INFO, "Done :)");
    }

    private static void addMetaToImageType(JSONObject jsonObject) {
        String jsonKey = "media--image";
        for (String key : jsonObject.keySet()) {
            if (key.equals(jsonKey)) {
                JSONObject meta = jsonObject.getJSONObject("media--image").getJSONObject("properties").getJSONObject("meta");
                meta.put(TITLE, "image metadata");
                meta.put("properties", new JSONObject("{\n" +
                        "            \"alt\": {\n" +
                        "              \"type\": \"string\",\n" +
                        "              \"title\": \"image alt\"\n" +
                        "            },\n" +
                        "            \"title\": {\n" +
                        "              \"type\": \"string\",\n" +
                        "              \"title\": \"image title\"\n" +
                        "            }\n" +
                        "          }"));
                logger.log(Level.INFO, "MEEEETA:: {0}", key);
            } else if (jsonObject.get(key) instanceof JSONObject) {
                addMetaToImageType((JSONObject) jsonObject.get(key));
            }
        }
    }

    private static void correctBreadcrumbType(JSONObject jsonObject) {
        String jsonKey = "breadcrumbs";
        Map<String, JSONObject> datas = new HashMap<>();

        for (String key : jsonObject.keySet()) {
            if (key.equals(jsonKey)) {
                datas.put(key, jsonObject.getJSONObject(key));
            } else if (jsonObject.get(key) instanceof JSONObject) {
                correctBreadcrumbType((JSONObject) jsonObject.get(key));
            }
        }

        datas.forEach((key, value) -> {
            JSONObject breadcrumbCurrentSchema = jsonObject.getJSONObject(jsonKey);

            if (breadcrumbCurrentSchema.has("type") && breadcrumbCurrentSchema.get("type").equals(ARRAY)) {
                return;
            }

            JSONObject breadcrumbCorrectedSchema = new JSONObject();
            breadcrumbCorrectedSchema.put("type", ARRAY);
            breadcrumbCorrectedSchema.put(ITEMS, breadcrumbCurrentSchema);

            jsonObject.remove(jsonKey);
            jsonObject.put(jsonKey, breadcrumbCorrectedSchema);
        });
    }

    private static void correctTimestamps(JSONObject jsonObject) {
        String jsonKey = "format";
        for (String key : jsonObject.keySet()) {
            if (key.equals(jsonKey) && jsonObject.get(jsonKey) instanceof String && jsonObject.getString(jsonKey).equals("utc-millisec")) {
                jsonObject.put("type", STRING);
            } else if (jsonObject.get(key) instanceof JSONObject) {
                correctTimestamps((JSONObject) jsonObject.get(key));
            }
        }
    }

    private static void correctUriType(JSONObject jsonObject) {
        for (String key : jsonObject.keySet()) {
            if ( key.equals("value")) {
                JSONObject uriJsonObject = jsonObject.getJSONObject(key);
                if (uriJsonObject.has("type") && "uri".equals(uriJsonObject.get("type"))) {
                    uriJsonObject.put("type", STRING);
                }

            } else if( key.equals("uri")) {
                JSONObject uriJsonObject = jsonObject.getJSONObject(key);
                if (uriJsonObject.has(TITLE) && "URI".equals(uriJsonObject.getString(TITLE))) {
                    uriJsonObject.put(TITLE, "File URI");
                    if (jsonObject.get(key) instanceof JSONObject) {
                        correctUriType((JSONObject) jsonObject.get(key));
                    }
                }
            } else if (jsonObject.get(key) instanceof JSONObject) {
                correctUriType((JSONObject) jsonObject.get(key));
            }
        }
    }

    private static void correctLangcode(JSONObject jsonObject) {
        Map<String, JSONObject> datas = new HashMap<>();

        for (String key : jsonObject.keySet()) {
            if (key.equals(LANGCODE)) {
                datas.put(key, jsonObject.getJSONObject(key));
            } else if (jsonObject.get(key) instanceof JSONObject) {
                correctLangcode((JSONObject) jsonObject.get(key));
            }
        }

        datas.forEach((key, value) -> {
            jsonObject.remove(LANGCODE);
            jsonObject.put(LANGCODE, new JSONObject("{\"type\":\"string\",\"title\":\"Language\"}"));
        });
    }

    private static void correctDefinitions(JSONObject jsonObject) {
        String jsonKey = "properties";
        Map<String, JSONObject> datas = new HashMap<>();
        for (String key : jsonObject.keySet()) {
             if(jsonObject.getJSONObject(key).has(jsonKey) && jsonObject.getJSONObject(key).getJSONObject(jsonKey).has("data")) {
                 datas.put(key, jsonObject.getJSONObject(key).getJSONObject(jsonKey).getJSONObject("data"));
             }
        }

        datas.forEach((key, value) -> {
            JSONObject props = jsonObject.getJSONObject(key).getJSONObject(jsonKey);
            props.remove("data");
            props.put("data", new JSONObject(String.format("{\"$ref\": \"#\\/definitions\\/%s%s\"}", key, DATA_SUFFIX)));
            jsonObject.put(key + DATA_SUFFIX, value);
        });
    }

    private static InputStream getInputStreamREST(String inputSpec, String user, String password) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(null, (certificate, authType) -> true).build();
        CloseableHttpClient httpClient = HttpClients.custom().setSSLContext(sslContext)
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .build();

        HttpGet httpGet = new HttpGet(inputSpec);

        if(StringUtils.isNotBlank(user) && StringUtils.isNotBlank(password)) {
            String auth = String.format("%s:%s", user, password);
            httpGet.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        }

        CloseableHttpResponse response = httpClient.execute(httpGet);
        if(response.getStatusLine().getStatusCode()!=200){
            throw new IOException("Cannot read inputspec from: " + inputSpec + "\nstatuscode: " + response.getStatusLine().getStatusCode());
        }
        final HttpEntity entity = response.getEntity();
        return entity.getContent();
    }


    private static void removeSecurityField(JSONObject object) {
        logger.log(Level.INFO, "Removing security field");
        object.remove("security");
    }

    private static void addIncludeAndFilterQueryParam(JSONObject jsonObject) {
        String jsonKey = "parameters";
        for (String key : jsonObject.keySet()) {
            if (key.equals(jsonKey) ) {
                boolean hasIncludeParam = false;
                for (Object param : jsonObject.getJSONArray(jsonKey)) {
                    if(((JSONObject) param).has("name") && ((JSONObject) param).get("name").equals("include")) {
                        hasIncludeParam = true;
                        break;
                    }
                }
                if(!hasIncludeParam) {
                    jsonObject.getJSONArray(jsonKey).put(new JSONObject("{\"in\":\"query\",\"name\":\"include\",\"description\":\"include relation data\",\"required\":false,\"type\":\"string\"}"));
                }

                jsonObject.getJSONArray(jsonKey).put(new JSONObject("{\"in\":\"query\",\"name\":\"filter[field_path][value]\",\"description\":\"filter by path\",\"required\":false,\"type\":\"string\"}"));
            } else if (jsonObject.get(key) instanceof JSONObject) {
                addIncludeAndFilterQueryParam((JSONObject) jsonObject.get(key));
            }
        }
    }

    private static void deleteEmptyRequiredFields(JSONObject jsonObject) {
        String jsonKey = "required";
        for (String key : jsonObject.keySet()) {
            if (key.equals(jsonKey) ) {
                if(jsonObject.getJSONArray(jsonKey).isEmpty()) {
                    jsonObject.remove(jsonKey);
                }
            } else if (jsonObject.get(key) instanceof JSONObject) {
                deleteEmptyRequiredFields((JSONObject) jsonObject.get(key));
            }
        }
    }

    private static void updateParameters(JSONObject jsonObject) {
        String jsonKey = "parameters";
        for (String key : jsonObject.keySet()) {
            if (key.equals(jsonKey) ) {
                jsonObject.getJSONArray(jsonKey).forEach( o -> {
                    JSONObject jo = (JSONObject) o;
                    if(jo.get("type").equals(ARRAY) && !jo.has(ITEMS)){
                        jo.put(ITEMS, new JSONObject("{\"type\":\"string\"}"));
                    }
                });
            } else if (jsonObject.get(key) instanceof JSONObject) {
                    updateParameters((JSONObject) jsonObject.get(key));

            }

        }
    }

    private static void cleanPaths(JSONObject object) {
        logger.log(Level.INFO, "Removing patch, post, delete paths");
        JSONObject paths = object.getJSONObject("paths");
        Iterator<String> keys = paths.keys();
        List<String> removePaths = new ArrayList<>();
        while(keys.hasNext()) {
            String key = keys.next();
            if (paths.get(key) instanceof JSONObject) {
               ((JSONObject) paths.get(key)).remove("post");
               ((JSONObject) paths.get(key)).remove("patch");
               ((JSONObject) paths.get(key)).remove("delete");
            }
            if (paths.get(key) instanceof JSONObject && (((JSONObject) paths.get(key)).isEmpty() || !key.startsWith("/")) ) {
                removePaths.add(key);
            }
        }
        removePaths.forEach(paths::remove);
    }
}
