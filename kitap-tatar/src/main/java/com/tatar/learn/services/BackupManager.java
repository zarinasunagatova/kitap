package com.tatar.learn.services;

import com.tatar.learn.models.Word;
import com.tatar.learn.utils.JsonUtils;
import com.tatar.learn.utils.WordsExport;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackupManager {
    private static BackupManager instance;
    private DatabaseService dbService;
    private ScheduledExecutorService scheduler;
    private final String BACKUP_DIR = "backups";
    private final String DATA_DIR = "data";
    
    private BackupManager() {
        dbService = DatabaseService.getInstance();
        createBackupDirectory();
        startAutoBackup();
    }
    
    public static BackupManager getInstance() {
        if (instance == null) {
            instance = new BackupManager();
        }
        return instance;
    }
    
    private void createBackupDirectory() {
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
            System.out.println("📁 Создана папка для бэкапов: " + BACKUP_DIR);
        }
    }
    
    /**
     * Автоматический бэкап при старте (раз в день)
     */
    private void startAutoBackup() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // Первый бэкап через 1 минуту после запуска
        scheduler.schedule(this::createDailyBackup, 1, TimeUnit.MINUTES);
        
        // Затем каждый день в 3 часа ночи (упрощенно - каждый 24 часа)
        scheduler.scheduleAtFixedRate(this::createDailyBackup, 24, 24, TimeUnit.HOURS);
        
        System.out.println("🔄 Автоматическое резервное копирование запущено");
    }
    
    /**
     * Создание ежедневного бэкапа
     */
    public void createDailyBackup() {
        try {
            String fileName = BACKUP_DIR + "/daily_" + LocalDate.now() + ".json";
            File backupFile = new File(fileName);
            
            // Если сегодняшний бэкап уже есть - не создаем новый
            if (backupFile.exists()) {
                return;
            }
            
            createBackup(fileName);
            System.out.println("📀 Создан ежедневный бэкап: " + fileName);
            
            // Удаляем старые бэкапы (старше 30 дней)
            cleanOldBackups(30);
            
        } catch (Exception e) {
            System.err.println("❌ Ошибка при создании ежедневного бэкапа: " + e.getMessage());
        }
    }
    
    /**
     * Создание бэкапа при закрытии приложения
     */
    public void createShutdownBackup() {
        try {
            String fileName = BACKUP_DIR + "/shutdown_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".json";
            
            createBackup(fileName);
            System.out.println("📀 Создан бэкап при завершении: " + fileName);
            
        } catch (Exception e) {
            System.err.println("❌ Ошибка при создании бэкапа при завершении: " + e.getMessage());
        }
    }
    
    /**
     * Создание ручного бэкапа
     */
    public String createManualBackup() {
        try {
            String fileName = BACKUP_DIR + "/manual_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json";
            
            createBackup(fileName);
            return fileName;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Общий метод создания бэкапа
     */
    private void createBackup(String fileName) throws IOException {
        List<Word> words = dbService.getAllWords();
        
        WordsExport export = new WordsExport();
        export.setWords(words);
        export.setCategories(extractCategories(words));
        export.setExportDate(LocalDate.now());
        export.setTotalWords(words.size());
        
        JsonUtils.saveToFile(export, fileName);
    }
    
    private List<String> extractCategories(List<Word> words) {
        return words.stream()
            .map(Word::getCategory)
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Удаление старых бэкапов
     */
    private void cleanOldBackups(int daysToKeep) {
        try {
            Path backupPath = Paths.get(BACKUP_DIR);
            LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupPath, "*.json")) {
                for (Path entry : stream) {
                    String filename = entry.getFileName().toString();
                    
                    // Парсим дату из имени файла (для daily_YYYY-MM-DD.json)
                    if (filename.startsWith("daily_")) {
                        String dateStr = filename.substring(6, 16); // "daily_2026-02-28.json" -> "2026-02-28"
                        LocalDate fileDate = LocalDate.parse(dateStr);
                        
                        if (fileDate.isBefore(cutoffDate)) {
                            Files.delete(entry);
                            System.out.println("🗑️ Удален старый бэкап: " + filename);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при очистке старых бэкапов: " + e.getMessage());
        }
    }
    
    /**
     * Остановка планировщика при завершении
     */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }
}