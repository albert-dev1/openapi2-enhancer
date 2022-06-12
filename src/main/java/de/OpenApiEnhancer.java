package de;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class OpenApiEnhancer {

    public static final String DATA_SUFFIX = "--data";

    static void processOpenApiSpec(String inputSpec, String outputSpec) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (inputSpec == null) {
            throw new NullPointerException("No inputSpec file was provided");
        }


        System.out.println("Reading inputSpec from: " + inputSpec);
        InputStream is = inputSpec.startsWith("http") ? getInputStreamREST(inputSpec) : new FileInputStream(inputSpec);

        if (is == null) {
            throw new NullPointerException("Cannot load inputSpec");
        }

        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);

        removeSecurityField(object);
        cleanPaths(object);
        System.out.println("Updating parameter objects");
        updateParameters(object);
        System.out.println("Deleting empty require fields");
        deleteEmptyRequiredFields(object);
        System.out.println("Adding include and filterpath query params");
        addIncludeAndFilterQueryParam(object);
        System.out.println("Correcting definitions");
        correctDefinitions(object.getJSONObject("definitions"));
        System.out.println("Correcting Timestamp Parse Error");
        correctTimestamps(object);

        String jsonStr = object.toString(2)
                .replace("language_reference", "string")
                .replace("\\/", "/")
                .replace("/properties/data", DATA_SUFFIX);

        try {
            System.out.println("Writing outputSpec to: " + outputSpec);
            FileWriter myWriter = new FileWriter(outputSpec);
            myWriter.write(jsonStr);
            myWriter.close();
        } catch (IOException e) {
            System.out.println("Cannot write outputSpec.");
            e.printStackTrace();
        }
        System.out.println("Done :)");
    }

    private static void correctTimestamps(JSONObject jsonObject) {
        String jsonKey = "format";
        for (String key : jsonObject.keySet()) {
            if (key.equals(jsonKey) && jsonObject.get(jsonKey) instanceof String && jsonObject.getString(jsonKey).equals("utc-millisec")) {
                jsonObject.put("type", "string");
            } else if (jsonObject.get(key) instanceof JSONObject) {
                correctTimestamps((JSONObject) jsonObject.get(key));
            }
        }
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

    private static InputStream getInputStreamREST(String inputSpec) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, (certificate, authType) -> true).build();
            CloseableHttpClient httpClient = HttpClients.custom().setSSLContext(sslContext)
                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
                    .build();

        HttpGet httpGet = new HttpGet(inputSpec);
        CloseableHttpResponse response = httpClient.execute(httpGet);
        final HttpEntity entity = response.getEntity();
        return entity.getContent();
        //return new URL(inputSpec).openStream();
    }


    private static void removeSecurityField(JSONObject object) {
        System.out.println("Removing security field");
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
                    if(jo.get("type").equals("array") && !jo.has("items")){
                        jo.put("items", new JSONObject("{\"type\":\"string\"}"));
                    }
                });
            } else if (jsonObject.get(key) instanceof JSONObject) {
                    updateParameters((JSONObject) jsonObject.get(key));

            }

        }
    }

    private static void cleanPaths(JSONObject object) {
        System.out.println("Removing patch, post, delete paths");
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
