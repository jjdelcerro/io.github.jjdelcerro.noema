/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.github.jjdelcerro.noema.lib.impl.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class ModelUtils {

  public static Map<String, Object>  parseModel(String modelInfo) {
    Gson gson = new Gson();
    JsonObject modelInfo_j = gson.fromJson(modelInfo, JsonObject.class);
    Map<String, Object> modelInfo_d = jsonObjectToMap(modelInfo_j);
    return modelInfo_d;
  }

    private static Object jsonElementToObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                // Para números, devolvemos Double o Integer según corresponda
                Number number = primitive.getAsNumber();
                if (number instanceof Integer || number instanceof Long) {
                    return number.longValue();
                } else {
                    return number.doubleValue();
                }
            } else if (primitive.isString()) {
                return primitive.getAsString();
            }
        }
        if (element.isJsonObject()) {
            return jsonObjectToMap(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            return jsonArrayToList(element.getAsJsonArray());
        }
        return null; // no debería ocurrir
    }

    private static Map<String, Object> jsonObjectToMap(JsonObject jsonObject) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            map.put(entry.getKey(), jsonElementToObject(entry.getValue()));
        }
        return map;
    }

    private static List<Object> jsonArrayToList(JsonArray jsonArray) {
        List<Object> list = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            list.add(jsonElementToObject(element));
        }
        return list;
    }
  
}
