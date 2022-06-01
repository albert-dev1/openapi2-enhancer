package de;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        String resourceName = "/openapi.json";
        InputStream is = Main.class.getResourceAsStream(resourceName);
        if (is == null) {
            throw new NullPointerException("Cannot find resource file " + resourceName);
        }

        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);

        removeSecurityField(object);
        cleanPaths(object);
        System.out.println("Updating parameter objects");
        updateParameters(object);
        System.out.println("Deleting empty require fields");
        deleteEmptyRequiredFields(object);
        System.out.println("Adding include query params");
        addIncludeQueryParam(object);


        String jsonStr = object.toString(2)
                .replace("language_reference", "string")
                .replace("\\/", "/");

        try {
            FileWriter myWriter = new FileWriter("openapi2.json");
            myWriter.write(jsonStr);
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void removeSecurityField(JSONObject object) {
        System.out.println("Removing security field");
        object.remove("security");
    }

    private static JSONObject addIncludeQueryParam(JSONObject jsonObject) {
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
            } else if (jsonObject.get(key) instanceof JSONObject) {
                addIncludeQueryParam((JSONObject) jsonObject.get(key));
            }
        }
        return jsonObject;
    }

    private static JSONObject deleteEmptyRequiredFields(JSONObject jsonObject) {
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
        return jsonObject;
    }

    private static JSONObject updateParameters(JSONObject jsonObject) {
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
        return jsonObject;
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
        removePaths.forEach(path -> paths.remove(path));
    }
}
