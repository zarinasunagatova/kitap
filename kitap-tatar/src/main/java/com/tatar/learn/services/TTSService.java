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
    private String platformSupportStatus;
    
    private TTSService() {
        osName = System.getProperty("os.name").toLowerCase();
        detectPlatformAndVoice();
    }
    
    public static TTSService getInstance() {
        if (instance == null) {
            instance = new TTSService();
        }
        return instance;
    }
    
    /**
     * Определяет платформу и наличие татарского голоса
     */
    private void detectPlatformAndVoice() {
        System.out.println("🖥️ Operating System: " + osName);
        
        if (osName.contains("win")) {
            System.out.println("📢 Using Windows SAPI5 for TTS");
            detectWindowsVoice();
        } 
        else if (osName.contains("linux")) {
            System.out.println("📢 Using Linux Speech Dispatcher for TTS");
            detectLinuxVoice();
        }
        else if (osName.contains("mac")) {
            // macOS НЕ ПОДДЕРЖИВАЕТ татарский TTS
            detectMacVoice();
        }
        else {
            platformSupportStatus = "❌ TTS не поддерживается на этой ОС";
            System.out.println("❌ Unsupported OS for TTS");
            rhVoiceAvailable = false;
        }
    }
    
    /**
     * Windows: поиск голоса через PowerShell/SAPI5
     */
    @SuppressWarnings("deprecation")
	private void detectWindowsVoice() {
        try {
            String command = "powershell -Command \"" +
                "Add-Type -AssemblyName System.Speech; " +
                "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$synth.GetInstalledVoices() | ForEach-Object { $_.VoiceInfo.Name }\"";

            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "CP866")
            );

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("talgat") || 
                        lowerLine.contains("talgon") || 
                        lowerLine.contains("rhvoice")) {
                        tatarVoice = line;
                        rhVoiceAvailable = true;
                        platformSupportStatus = "✅ Татарский голос: " + tatarVoice;
                        System.out.println("✅ Found Tatar voice: " + tatarVoice);
                        return;
                    }
                }
            }
            
            platformSupportStatus = "⚠️ Татарский голос не найден\nУстановите RHVoice с голосом Talgat";
            System.out.println(platformSupportStatus);
            rhVoiceAvailable = false;
            
        } catch (Exception e) {
            System.err.println("Error detecting Windows voice: " + e.getMessage());
            platformSupportStatus = "❌ Ошибка определения голоса";
            rhVoiceAvailable = false;
        }
    }
    
    /**
     * Linux: проверка Speech Dispatcher и RHVoice
     */
    private void detectLinuxVoice() {
        try {
            ProcessBuilder checkPb = new ProcessBuilder("which", "speech-dispatcher");
            Process checkProcess = checkPb.start();
            int exitCode = checkProcess.waitFor();
            
            if (exitCode != 0) {
                platformSupportStatus = "⚠️ speech-dispatcher не установлен\n" +
                    "Установите: sudo apt-get install speech-dispatcher rhvoice rhvoice-tatal";
                System.out.println(platformSupportStatus);
                rhVoiceAvailable = false;
                return;
            }
            
            String[] checkVoiceCmd = {
                "sh", "-c", "spd-say -o rhvoice --help 2>&1 | grep -i talgat"
            };
            
            Process voiceCheck = Runtime.getRuntime().exec(checkVoiceCmd);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(voiceCheck.getInputStream())
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("talgat") || 
                    line.toLowerCase().contains("tatar")) {
                    tatarVoice = "rhvoice-talgat";
                    rhVoiceAvailable = true;
                    platformSupportStatus = "✅ Татарский голос: RHVoice/Talgat";
                    System.out.println("✅ Found Tatar voice on Linux");
                    return;
                }
            }
            
            platformSupportStatus = "⚠️ RHVoice установлен, но голос Talgat не найден\n" +
                "Установите: sudo apt-get install rhvoice rhvoice-tatal";
            rhVoiceAvailable = false;
            
        } catch (Exception e) {
            System.err.println("Error detecting Linux voice: " + e.getMessage());
            platformSupportStatus = "⚠️ Ошибка определения голоса\n" +
                "Установите RHVoice: https://github.com/RHVoice/RHVoice";
            rhVoiceAvailable = false;
        }
    }
    
    /**
     * macOS: ТАТАРСКИЙ TTS НЕ ПОДДЕРЖИВАЕТСЯ
     * RHVoice для macOS не включает татарский язык
     */
    private void detectMacVoice() {
        // Официально: RHVoice на macOS НЕ поддерживает татарский язык
        // Источник: https://github.com/RHVoice/RHVoice
        platformSupportStatus = "❌ Татарский голос не поддерживается на macOS\n" +
            "RHVoice для macOS не включает татарский язык.\n" +
            "Рекомендуется использовать Windows или Linux для озвучивания на татарском.";
        
        System.out.println(platformSupportStatus);
        rhVoiceAvailable = false;
        tatarVoice = null;
    }
    
    /**
     * Произносит текст (кроссплатформенная версия)
     */
    public CompletableFuture<Void> speak(String text) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        if (!rhVoiceAvailable) {
            // На macOS просто логируем, без звука
            System.out.println("🔊 (TTS недоступен) " + text);
            future.complete(null);
            return future;
        }
        
        try {
            if (osName.contains("win")) {
                speakWindows(text, future);
            } 
            else if (osName.contains("linux")) {
                speakLinux(text, future);
            }
            else {
                // macOS и другие ОС сюда не попадают (rhVoiceAvailable = false)
                future.completeExceptionally(new Exception("TTS not supported on this OS"));
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Windows: через PowerShell + SAPI5
     */
    private void speakWindows(String text, CompletableFuture<Void> future) {
        try {
            String escapedText = text.replace("'", "''");
            String command = String.format(
                "powershell -Command \"" +
                "Add-Type -AssemblyName System.Speech; " +
                "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "try { $synth.SelectVoice('%s'); } catch { } " +
                "$synth.Speak('%s');\"",
                tatarVoice, escapedText
            );
            
            @SuppressWarnings("deprecation")
			Process process = Runtime.getRuntime().exec(command);
            new Thread(() -> {
                try {
                    process.waitFor();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).start();
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }
    
    /**
     * Linux: через Speech Dispatcher
     */
    private void speakLinux(String text, CompletableFuture<Void> future) {
        try {
            String escapedText = text.replace("'", "'\\''");
            
            String[] command = {
                "sh", "-c", 
                String.format("spd-say -o rhvoice -e '%s'", escapedText)
            };
            
            Process process = Runtime.getRuntime().exec(command);
            new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        future.complete(null);
                    } else {
                        String[] fallbackCmd = {"sh", "-c", String.format("spd-say '%s'", escapedText)};
                        Process fallback = Runtime.getRuntime().exec(fallbackCmd);
                        fallback.waitFor();
                        future.complete(null);
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).start();
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }
    
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
    
    public boolean isAvailable() {
        return rhVoiceAvailable;
    }
    
    public String getStatus() {
        if (platformSupportStatus != null) {
            return platformSupportStatus;
        }
        if (rhVoiceAvailable) {
            return "✅ Татарский голос: " + tatarVoice;
        }
        return "❌ Татарский голос не найден";
    }
    
    public void refresh() {
        rhVoiceAvailable = false;
        tatarVoice = null;
        detectPlatformAndVoice();
    }
}