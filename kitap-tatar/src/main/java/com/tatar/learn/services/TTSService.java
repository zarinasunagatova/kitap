package com.tatar.learn.services;

import javafx.application.Platform;
import javafx.scene.control.Button;
import java.io.*;
import java.util.concurrent.CompletableFuture;

public class TTSService {
    private static TTSService instance;
    private String tatarVoice = null;
    private boolean rhVoiceAvailable = false;
    private String osName;
    
    private TTSService() {
        osName = System.getProperty("os.name").toLowerCase();
        detectTatarVoice();
    }
    
    public static TTSService getInstance() {
        if (instance == null) {
            instance = new TTSService();
        }
        return instance;
    }
    
    /**
     * Определяет наличие татарского голоса в системе
     */
    private void detectTatarVoice() {
        System.out.println("🔍 Поиск татарского голоса...");
        
        try {
            // PowerShell команда для получения списка голосов
            String command = "powershell -Command \"" +
                "Add-Type -AssemblyName System.Speech; " +
                "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$synth.GetInstalledVoices() | " +
                "ForEach-Object { $_.VoiceInfo.Name }\"";
            
            Process process = Runtime.getRuntime().exec(command);
            
            // Читаем вывод (используем кодировку CP866 для Windows)
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "CP866")
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    System.out.println("  Найден голос: " + line);
                    
                    // Ищем различные варианты названия татарского голоса
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("talgat") || 
                        lowerLine.contains("talgon") || 
                        lowerLine.contains("татар") ||
                        lowerLine.contains("tatar") ||
                        lowerLine.contains("rhvoice")) {
                        
                        tatarVoice = line;
                        rhVoiceAvailable = true;
                        System.out.println("✅ Найден татарский голос: " + tatarVoice);
                        
                        // Пробуем воспроизвести тестовое сообщение
                        testVoice();
                        return;
                    }
                }
            }
            
            // Если не нашли, пробуем альтернативные названия
            String[] possibleVoices = {"Talgat", "Talgon", "RHVoice", "Татар", "Tatar"};
            for (String voice : possibleVoices) {
                if (checkVoiceExists(voice)) {
                    tatarVoice = voice;
                    rhVoiceAvailable = true;
                    System.out.println("✅ Найден голос через прямой поиск: " + voice);
                    testVoice();
                    return;
                }
            }
            
            System.out.println("❌ Татарский голос не найден");
            
        } catch (Exception e) {
            System.err.println("Ошибка при поиске голоса: " + e.getMessage());
            rhVoiceAvailable = false;
        }
    }
    
    /**
     * Проверяет существование конкретного голоса
     */
    private boolean checkVoiceExists(String voiceName) {
        try {
            String command = String.format(
                "powershell -Command \"" +
                "Add-Type -AssemblyName System.Speech; " +
                "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "try { $synth.SelectVoice('%s'); Write-Host 'OK' } catch { Write-Host 'FAIL' }\"",
                voiceName
            );
            
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "CP866")
            );
            
            String result = reader.readLine();
            return result != null && result.contains("OK");
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Тестирует голос (тихо, для проверки)
     */
    private void testVoice() {
        try {
            String testCommand = String.format(
                "powershell -Command \"" +
                "Add-Type -AssemblyName System.Speech; " +
                "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$synth.SelectVoice('%s'); " +
                "$synth.Volume = 30; " +  // тихо
                "$synth.Speak('Test');\"",
                tatarVoice
            );
            Runtime.getRuntime().exec(testCommand);
        } catch (Exception e) {
            // Игнорируем, это просто тест
        }
    }
    
    /**
     * Произносит текст
     */
    public CompletableFuture<Void> speak(String text) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        if (!rhVoiceAvailable) {
            System.out.println("🔊 (без звука) " + text);
            future.complete(null);
            return future;
        }
        
        try {
            // Экранируем кавычки
            String escapedText = text.replace("'", "''");
            
            String command = String.format(
                "powershell -Command \"" +
                "Add-Type -AssemblyName System.Speech; " +
                "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "try { $synth.SelectVoice('%s'); } catch { } " +
                "$synth.Speak('%s');\"",
                tatarVoice, escapedText
            );
            
            Process process = Runtime.getRuntime().exec(command);
            
            new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    
                    if (exitCode == 0) {
                        future.complete(null);
                    } else {
                        // Если не сработало, пробуем без выбора голоса
                        fallbackSpeak(text, future);
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).start();
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Запасной вариант - без выбора конкретного голоса
     */
    private void fallbackSpeak(String text, CompletableFuture<Void> originalFuture) {
        try {
            String escapedText = text.replace("'", "''");
            String command = String.format(
                "powershell -Command \"" +
                "Add-Type -AssemblyName System.Speech; " +
                "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$synth.Speak('%s');\"",
                escapedText
            );
            
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            originalFuture.complete(null);
            
        } catch (Exception e) {
            originalFuture.completeExceptionally(e);
        }
    }
    
    /**
     * Произносит с обратной связью для кнопки
     */
    public void speakWithFeedback(String text, Button button, String originalText) {
        Platform.runLater(() -> {
            button.setText("🔊 ...");
            button.setDisable(true);
        });
        
        speak(text).whenComplete((result, error) -> {
            Platform.runLater(() -> {
                button.setText(originalText);
                button.setDisable(false);
            });
            
            if (error != null) {
                Platform.runLater(() -> 
                    System.err.println("Ошибка озвучивания: " + error.getMessage())
                );
            }
        });
    }
    
    /**
     * Проверка доступности голоса
     */
    public boolean isAvailable() {
        return rhVoiceAvailable;
    }
    
    /**
     * Получение статуса для отображения
     */
    public String getStatus() {
        if (rhVoiceAvailable) {
            return "✅ Татарский голос: " + tatarVoice;
        } else {
            return "❌ Татарский голос не найден";
        }
    }
    
    /**
     * Принудительная проверка голоса (можно вызвать из настроек)
     */
    public void refresh() {
        rhVoiceAvailable = false;
        tatarVoice = null;
        detectTatarVoice();
    }
}