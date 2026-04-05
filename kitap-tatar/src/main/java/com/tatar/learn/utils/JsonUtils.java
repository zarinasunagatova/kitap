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
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class JsonUtils {
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
        .registerTypeAdapter(new TypeToken<List<String>>(){}.getType(), new StringListAdapter())
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
    
    // Конвертировать JSON строку в объект с типом
    public static <T> T fromJson(String json, Type typeOfT) {
        return gson.fromJson(json, typeOfT);
    }
    
    // Экспорт слов в JSON с примерами
    public static void exportWords(List<com.tatar.learn.models.Word> words, String filePath) throws IOException {
        WordsExport export = new WordsExport();
        export.setWords(words);
        export.setExportDate(LocalDate.now());
        export.setTotalWords(words.size());
        
        // Собираем уникальные категории
        java.util.Set<String> categorySet = new java.util.HashSet<>();
        for (com.tatar.learn.models.Word word : words) {
            if (word.getCategory() != null && !word.getCategory().isEmpty()) {
                categorySet.add(word.getCategory());
            }
        }
        export.setCategories(new java.util.ArrayList<>(categorySet));
        
        saveToFile(export, filePath);
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

// Адаптер для списка строк (примеров)
class StringListAdapter extends TypeAdapter<List<String>> {
    @Override
    public void write(JsonWriter out, List<String> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (String str : value) {
            out.value(str);
        }
        out.endArray();
    }
    
    @Override
    public List<String> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        
        List<String> result = new java.util.ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            result.add(in.nextString());
        }
        in.endArray();
        return result;
    }
}