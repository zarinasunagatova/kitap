package com.tatar.learn.utils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class JsonUtils {
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())  // адаптер для LocalDate
        .create();
    
    // Сохранить объект в JSON файл
    public static void saveToFile(Object obj, String filePath) throws IOException {
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(obj, writer);
        }
    }
    
    // Прочитать объект из JSON файла
    public static <T> T loadFromFile(String filePath, Class<T> classOfT) throws IOException {
        try (Reader reader = new FileReader(filePath)) {
            return gson.fromJson(reader, classOfT);
        }
    }
    
    // Прочитать список из JSON файла
    public static <T> List<T> loadListFromFile(String filePath, Type type) throws IOException {
        try (Reader reader = new FileReader(filePath)) {
            return gson.fromJson(reader, type);
        }
    }
    
    // Конвертировать объект в JSON строку
    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }
    
    // Конвертировать JSON строку в объект
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }
}

// Адаптер для LocalDate
class LocalDateAdapter extends TypeAdapter<LocalDate> {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
    
    @Override
    public void write(JsonWriter out, LocalDate value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.format(formatter));
        }
    }
    
    @Override
    public LocalDate read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return LocalDate.parse(in.nextString(), formatter);
    }
}