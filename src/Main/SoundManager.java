package Main; // package name

import javax.sound.sampled.*; // audio playback
import java.io.File; // file operations

public class SoundManager { // manages music and sound effects
    private static Clip menuMusicClip; // menu music clip
    private static Clip gameMusicClip; // game music clip
    private static Clip developerMusicClip; // developer music clip
    private static boolean musicEnabled = true; // music toggle flag
    private static boolean sfxEnabled = true; // sound effects toggle flag
    private static boolean isDeveloperMode = false; // developer mode flag

    public static void playMenuMusic() { // play menu music (looping)
        stopMenuMusic(); // stop if already playing
        if (!musicEnabled) return;
        try {
            File musicFile = new File("sounds/Music/Punch Out.wav");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(musicFile);
            menuMusicClip = AudioSystem.getClip();
            menuMusicClip.open(audioStream);
            menuMusicClip.loop(Clip.LOOP_CONTINUOUSLY); // loop indefinitely
            menuMusicClip.start();
        } catch (Exception e) {
            System.err.println("Failed to play menu music: " + e.getMessage());
        }
    }

    public static void stopMenuMusic() { // stop menu music
        if (menuMusicClip != null && menuMusicClip.isRunning()) {
            menuMusicClip.stop();
            menuMusicClip.close();
            menuMusicClip = null;
        }
    }

    public static void playGameMusic() { // play game music (looping)
        stopGameMusic(); // stop if already playing
        stopDeveloperMusic(); // stop developer music if playing
        if (!musicEnabled) return;
        if (isDeveloperMode) {
            playDeveloperMusic(); // play developer music if in developer mode
            return;
        }
        try {
            File musicFile = new File("sounds/Music/11 Shoryuken.wav");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(musicFile);
            gameMusicClip = AudioSystem.getClip();
            gameMusicClip.open(audioStream);
            gameMusicClip.loop(Clip.LOOP_CONTINUOUSLY); // loop indefinitely
            gameMusicClip.start();
        } catch (Exception e) {
            System.err.println("Failed to play game music: " + e.getMessage());
        }
    }

    public static void stopGameMusic() { // stop game music
        if (gameMusicClip != null && gameMusicClip.isRunning()) {
            gameMusicClip.stop();
            gameMusicClip.close();
            gameMusicClip = null;
        }
    }

    public static void playDeveloperMusic() { // play developer music (looping)
        stopDeveloperMusic(); // stop if already playing
        stopGameMusic(); // stop normal game music
        if (!musicEnabled) return;
        try {
            File musicFile = new File("sounds/Music/09 Uppdercut.wav");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(musicFile);
            developerMusicClip = AudioSystem.getClip();
            developerMusicClip.open(audioStream);
            developerMusicClip.loop(Clip.LOOP_CONTINUOUSLY); // loop indefinitely
            developerMusicClip.start();
        } catch (Exception e) {
            System.err.println("Failed to play developer music: " + e.getMessage());
        }
    }

    public static void stopDeveloperMusic() { // stop developer music
        if (developerMusicClip != null && developerMusicClip.isRunning()) {
            developerMusicClip.stop();
            developerMusicClip.close();
            developerMusicClip = null;
        }
    }

    public static void setDeveloperMode(boolean enabled) { // set developer mode flag
        isDeveloperMode = enabled;
    }

    public static boolean isDeveloperMode() { return isDeveloperMode; }

    public static void playClickSound() { // play click sound effect
        if (!sfxEnabled) return;
        try {
            File soundFile = new File("sounds/sfx/click.wav");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start(); // play once
            // clip will be garbage collected after playing
        } catch (Exception e) {
            System.err.println("Failed to play click sound: " + e.getMessage());
        }
    }

    public static boolean isMusicEnabled() { return musicEnabled; }
    public static void setMusicEnabled(boolean enabled) {
        musicEnabled = enabled;
        if (!enabled) {
            stopMenuMusic();
            stopGameMusic();
            stopDeveloperMusic();
        } else {
            // restart music if needed (this will be handled by MainMenu/GamePanel)
        }
    }

    public static boolean isSfxEnabled() { return sfxEnabled; }
    public static void setSfxEnabled(boolean enabled) {
        sfxEnabled = enabled;
    }
}

