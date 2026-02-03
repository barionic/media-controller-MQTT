import org.eclipse.paho.client.mqttv3.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.UUID;

public class MediaController {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TOPIC = "/media/advtime/#";

    //Lista fixa p/ ordenação
    private static final String[] EPISODES = {"ep18", "ep22"};

    private static String currentSeason;
    private static String currentEpisode;

    private static Process vlcProcess;
    private static BufferedWriter vlcWriter;

    //======== MQTT ========

    public static void main(String[] args) throws MqttException {
        String clientId = "media-controller-" + UUID.randomUUID();

        MqttClient client = new MqttClient(BROKER_URL, clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        System.out.println("Conectando ao broker MQTT...");
        client.connect(options);
        System.out.println("Conectado com sucesso!");

        client.subscribe(TOPIC, MediaController::handleMessage);
        System.out.println("Aguardando comandos...");
    }

    private static void handleMessage(String topic, MqttMessage message) {
        System.out.println("Mensagem recebida");
        System.out.println("Tópico: " + topic);

        String[] parts = topic.split("/");

        if (parts.length < 6) {
            System.out.println("Tópico inválido");
            return;
        }

        String season = parts[3];
        String episode = parts[4];
        String action = parts[5];

        String videoPath = buildPath(season, episode);

        switch (action) {
            case "play" -> {
                if (!isVlcRunning() || isDifferentEpisode(season, episode)) {
                    ensureVlcWithMedia(videoPath, season, episode);
                }
                play();
            }
            case "pause" -> pause();
            case "begin" -> begin();
            case "next" -> changeEpisode(+1);
            case "prev" -> changeEpisode(-1);
            case "volup" -> volumeUp();
            case "voldown" -> volumeDown();
            case "exit" -> exitProgram();
            default -> System.out.println("Ação desconhecida: " + action);
        }
    }

    //======== EPISODES ========

    private static void changeEpisode(int delta) {
        if (currentEpisode == null) {
            System.out.println("Nenhum episódio em execução");
            return;
        }

        int index = indexOfEpisodes(currentEpisode);
        int newIndex = index + delta;

        if (newIndex < 0 || newIndex >= EPISODES.length) {
            System.out.println("Não há episódios nessa direção");
            return;
        }

        String nextEpisode = EPISODES[newIndex];
        String path = buildPath(currentSeason, nextEpisode);

        ensureVlcWithMedia(path, currentSeason, nextEpisode);
        play();
    }

    private static int indexOfEpisodes(String ep) {
        for (int i = 0; i < EPISODES.length; i++) {
            if (EPISODES[i].equals(ep)) {
                return i;
            }
        }
        return -1;
    }

    //======== VLC CORE ========

    private static boolean isVlcRunning() {
        return vlcProcess != null && vlcProcess.isAlive();
    }

    public static void startVlc(String path) throws IOException {
        System.out.println("Iniciando o VLC...");

        ProcessBuilder pb = new ProcessBuilder(
                "vlc",
                "--intf", "rc", // RC = Remote Control; aceite de input em texto
                "--quiet",
                path
        );
        vlcProcess = pb.start();
        vlcWriter = new BufferedWriter(new OutputStreamWriter(vlcProcess.getOutputStream()));
    }

    private static void ensureVlcWithMedia(String path, String season, String episode) {
        currentSeason = season;
        currentEpisode = episode;

        try {
            if (!isVlcRunning()) {
                startVlc(path);
                return;
            }
            sendVlcCommand("clear");
            sendVlcCommand("add " + path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendVlcCommand(String cmd) {
        try {
            System.out.println("VLC CMD: " + cmd);
            vlcWriter.write(cmd);
            vlcWriter.newLine();
            vlcWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //======== CONTROLS ========

    private static void play() {
        if (!isVlcRunning()) return;
        sendVlcCommand("play");
    }

    private static void pause() {
        if (!isVlcRunning()) return;
        sendVlcCommand("pause");
    }

    private static void stop() {
        if (!isVlcRunning()) return;
        sendVlcCommand("stop");
    }

    private static void begin() {
        if (!isVlcRunning()) return;
        sendVlcCommand("seek 0");
        sendVlcCommand("play");
    }

    private static void volumeUp() {
        if (!isVlcRunning()) return;
        sendVlcCommand("volup");
    }

    private static void volumeDown() {
        if (!isVlcRunning()) return;
        sendVlcCommand("voldown");
    }

    private static void exitProgram() {
        System.out.println("Encerrando MediaController...");
        stop();
        System.exit(0);
    }

    //======== UTILS ========

    private static String buildPath(String season, String episode) {
        return "/media/advtime/" + season + "/" + episode + ".mp4";
    }

    private static boolean isDifferentEpisode(String season, String episode) {
        return !season.equals(currentSeason) || !episode.equals(currentEpisode);
    }

}