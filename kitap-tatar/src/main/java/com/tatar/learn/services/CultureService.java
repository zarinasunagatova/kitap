package com.tatar.learn.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatar.learn.models.CultureItem;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CultureService {
    private static CultureService instance;
    private List<CultureItem> allItems;
    private final Gson gson = new Gson();
    
    private CultureService() {
        loadCultureItems();
    }
    
    public static CultureService getInstance() {
        if (instance == null) {
            instance = new CultureService();
        }
        return instance;
    }
    
    private void loadCultureItems() {
        try {
            InputStream is = getClass().getResourceAsStream("/data/culture/culture.json");
            if (is != null) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Type listType = new TypeToken<List<CultureItem>>(){}.getType();
                allItems = gson.fromJson(json, listType);
                System.out.println("Загружено культурных объектов: " + allItems.size());
            } else {
                createDefaultItems();
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки культуры: " + e.getMessage());
            createDefaultItems();
        }
    }
    
    private void createDefaultItems() {
        allItems = new ArrayList<>();
        
        // Праздники
        allItems.add(new CultureItem(1, "Сабантуй", "Сабантуй", 
            "Татарский национальный праздник плуга, посвященный окончанию весенних полевых работ.",
            "Сабантуй - басу эшләре тәмамлануга багышланган татар милли бәйрәме.",
            "holiday", "🎉"));
        
        allItems.add(new CultureItem(2, "Курбан-байрам", "Корбан бәйрәме",
            "Исламский праздник жертвоприношения, один из главных мусульманских праздников.",
            "Корбан чалу бәйрәме - төп мөселман бәйрәмнәренең берсе.",
            "holiday", "🕌"));
        
        // Кухня
        allItems.add(new CultureItem(3, "Эчпочмак", "Өчпочмак",
            "Татарский пирожок с начинкой из мяса, картофеля и лука, выпекаемый в духовке.",
            "Мясо, бәрәңге һәм суган тултырмасы белән татар бәлеше.",
            "food", "🥧"));
        
        allItems.add(new CultureItem(4, "Чак-чак", "Чәк-чәк",
            "Традиционное татарское лакомство из меда и теста.",
            "Бал һәм камырдан ясалган традицион татар тәмле ашы.",
            "food", "🍯"));
        
        // Музыка
        allItems.add(new CultureItem(5, "Курай", "Курай",
            "Татарский духовой музыкальный инструмент, сделанный из стебля растения.",
            "Үсемлек сабагыннан ясалган татар музыка коралы.",
            "music", "🎵"));
        
        // Литература
        allItems.add(new CultureItem(6, "Габдулла Тукай", "Габдулла Тукай",
            "Великий татарский поэт, основоположник татарской литературы.",
            "Бөек татар шагыйре, татар әдәбиятына нигез салучы.",
            "literature", "📚"));
        
        allItems.add(new CultureItem(7, "Муса Джалиль", "Муса Җәлил",
            "Татарский поэт-герой, Герой Советского Союза.",
            "Татар шагыйрь-герое, Советлар Союзы Герое.",
            "literature", "📖"));
        
        // Одежда
        allItems.add(new CultureItem(8, "Калфак", "Калфак",
            "Традиционный татарский женский головной убор.",
            "Татар хатын-кыз баш киеме.",
            "clothing", "👒"));
        
        allItems.add(new CultureItem(9, "Тюбетейка", "Түбәтәй",
            "Мужской головной убор у тюркских народов.",
            "Төрки халыкларның ир-ат баш киеме.",
            "clothing", "🎩"));
        
        // Традиции
        allItems.add(new CultureItem(10, "Татарская свадьба", "Татар туе",
            "Богатая традициями церемония бракосочетания, длящаяся несколько дней.",
            "Берничә көн дәвам итүче, традицияләргә бай никах тантанасы.",
            "tradition", "💒"));
        
        System.out.println("Созданы дефолтные культурные объекты: " + allItems.size());
    }
    
    public List<CultureItem> getAllItems() {
        return allItems;
    }
    
    public List<CultureItem> getItemsByCategory(String category) {
        if (category == null || category.equals("Все")) {
            return getAllItems();
        }
        return allItems.stream()
            .filter(item -> category.equals(item.getCategory()))
            .toList();
    }
    
    public Set<String> getCategories() {
        Set<String> categories = new TreeSet<>();
        categories.add("Все");
        for (CultureItem item : allItems) {
            categories.add(item.getCategory());
        }
        return categories;
    }
    
    public CultureItem getItemById(int id) {
        return allItems.stream()
            .filter(item -> item.getId() == id)
            .findFirst()
            .orElse(null);
    }
}