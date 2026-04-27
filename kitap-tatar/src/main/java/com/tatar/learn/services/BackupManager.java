package com.tatar.learn.services;

import com.tatar.learn.models.Word;
import com.tatar.learn.utils.JsonUtils;
import com.tatar.learn.utils.WordsExport;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackupManager {
    private static volatile BackupManager instance;
    private static volatile boolean initFailed = false;
    private static volatile String initErrorMessage = null;
    
    private DatabaseService dbService;
    private ScheduledExecutorService scheduler;
    private boolean databaseAvailable = false;
    
    private final String BACKUP_DIR = "backups";
    
    private BackupManager() throws SQLException {
        try {
            dbService = DatabaseService.getInstance();
            
            if (dbService == null) {
                throw new SQLException("DatabaseService.getInstance() returned null");
            }
            
            if (!dbService.isHealthy()) {
                throw new SQLException("Database is not healthy");
            }
            
            databaseAvailable = true;
            createBackupDirectory();
            startAutoBackup();
            System.out.println("✅ BackupManager initialized successfully");
            
        } catch (SQLException e) {
            initFailed = true;
            initErrorMessage = e.getMessage();
            System.err.println("❌ BackupManager initialization failed: " + e.getMessage());
            throw e;
        }
    }
    
    public static BackupManager getInstance() throws SQLException {
        if (initFailed) {
            throw new SQLException("BackupManager initialization failed previously: " + initErrorMessage);
        }
        
        if (instance == null) {
            synchronized (BackupManager.class) {
                if (instance == null) {
                    instance = new BackupManager();
                }
            }
        }
        return instance;
    }
    
    public static boolean isInitialized() {
        return instance != null && !initFailed && instance.databaseAvailable;
    }
    
    public static void reset() {
        synchronized (BackupManager.class) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
            initFailed = false;
            initErrorMessage = null;
        }
    }
    
    private void checkDatabaseAvailable() throws SQLException {
        if (!databaseAvailable || dbService == null) {
            throw new SQLException("Database service is not available for backup");
        }
    }
    
    private void createBackupDirectory() {
        try {
            File backupDir = new File(BACKUP_DIR);
            if (!backupDir.exists()) {
                boolean created = backupDir.mkdirs();
                if (created) {
                    System.out.println("📁 Created backup directory: " + BACKUP_DIR);
                } else {
                    System.err.println("⚠️ Failed to create backup directory: " + BACKUP_DIR);
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error creating backup directory: " + e.getMessage());
        }
    }
    
    private void startAutoBackup() {
        if (!databaseAvailable) {
            System.err.println("⚠️ Auto backup disabled - database not available");
            return;
        }
        
        try {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(this::createDailyBackup, 1, TimeUnit.MINUTES);
            scheduler.scheduleAtFixedRate(this::createDailyBackup, 24, 24, TimeUnit.HOURS);
            System.out.println("🔄 Auto backup enabled (daily)");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to start auto backup: " + e.getMessage());
        }
    }
    
    public void createDailyBackup() {
        if (!databaseAvailable) {
            System.err.println("⚠️ Daily backup skipped - database not available");
            return;
        }
        
        try {
            String fileName = BACKUP_DIR + "/daily_" + LocalDate.now() + ".json";
            File backupFile = new File(fileName);
            
            if (backupFile.exists()) {
                return;
            }
            
            createBackup(fileName);
            System.out.println("📀 Daily backup created: " + fileName);
            cleanOldBackups(30);
            
        } catch (SQLException e) {
            System.err.println("❌ Database error in daily backup: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Error creating daily backup: " + e.getMessage());
        }
    }
    
    public void createShutdownBackup() {
        if (!databaseAvailable) {
            System.err.println("⚠️ Shutdown backup skipped - database not available");
            return;
        }
        
        try {
            String fileName = BACKUP_DIR + "/shutdown_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".json";
            
            createBackup(fileName);
            System.out.println("📀 Shutdown backup created: " + fileName);
            
        } catch (SQLException e) {
            System.err.println("❌ Database error in shutdown backup: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Error creating shutdown backup: " + e.getMessage());
        }
    }
    
    public String createManualBackup() {
        if (!databaseAvailable) {
            System.err.println("⚠️ Manual backup skipped - database not available");
            return null;
        }
        
        try {
            String fileName = BACKUP_DIR + "/manual_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json";
            
            createBackup(fileName);
            System.out.println("📀 Manual backup created: " + fileName);
            return fileName;
            
        } catch (SQLException e) {
            System.err.println("❌ Database error in manual backup: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("❌ Error creating manual backup: " + e.getMessage());
            return null;
        }
    }
    
    private void createBackup(String fileName) throws SQLException, IOException {
        checkDatabaseAvailable();
        
        List<Word> words = dbService.getAllWords();
        
        //  Создаём полноценный WordsExport через утилиту
        WordsExport export = new WordsExport();
        export.setWords(words);
        export.setExportDate(LocalDate.now());
        export.setTotalWords(words.size());
        // Статистика считается автоматически в setWords()
        
        // Сохраняем через JsonUtils
        JsonUtils.saveToFile(export, fileName);
    }
    
    private void cleanOldBackups(int daysToKeep) {
        try {
            Path backupPath = Paths.get(BACKUP_DIR);
            if (!Files.exists(backupPath)) {
                return;
            }
            
            LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupPath, "*.json")) {
                for (Path entry : stream) {
                    String filename = entry.getFileName().toString();
                    
                    if (filename.startsWith("daily_") && filename.length() >= 16) {
                        try {
                            String dateStr = filename.substring(6, 16);
                            LocalDate fileDate = LocalDate.parse(dateStr);
                            
                            if (fileDate.isBefore(cutoffDate)) {
                                Files.delete(entry);
                                System.out.println("🗑️ Deleted old backup: " + filename);
                            }
                        } catch (Exception e) {
                            System.err.println("⚠️ Could not parse date from: " + filename);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error cleaning old backups: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                System.out.println("🛑 Backup scheduler stopped");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}