import org.eclipse.paho.client.mqttv3.*;

import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.UUID;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class MediaController {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TOPIC = "/media/#";

    private static String currentSerie;
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

        // /media/{serie}
        String[] parts = topic.split("/");
        if (parts.length < 3) {
            System.out.println("Tópico inválido");
            return;
        }

        String serie = parts[2];

        String payload = new String(message.getPayload());
        System.out.println("Payload: " + payload);

        JSONObject json;
        try {
            json= new JSONObject(payload);
        } catch (Exception e){
            System.out.println("Payload inválido (Esperado JSON)");
            return;
        }

        String action = json.optString("action", "");

        switch (action) {
            case "play" -> handlePlay(serie, json);
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

    private static void handlePlay(String serie, JSONObject json){
        String season = json.optString("season", null);
        String episode = json.optString("episode", null);

        if (season != null && episode != null) {
            String path = buildPath(serie, season, episode);

            if (!isVlcRunning() || isDifferentEpisode(season, episode)) {
                ensureVlcWithMedia(path, serie, season, episode);
            }
        }
        play();
    }

    //======== EPISODES ========

    private static void changeEpisode(int delta) {
        if (currentEpisode == null || currentSeason == null || currentEpisode == null) {
            System.out.println("Nenhum episódio em execução");
            return;
        }

        List<String> episodes = listEpisodes(currentSerie, currentSeason);

        if (episodes.isEmpty()) {
            System.out.println("Nenhum episódio encontrado no filesystem");
            return;
        }

        int index = episodes.indexOf(currentEpisode);
        if (index == -1){
            System.out.println("Episódio atual não encontrado na lista");
            return;
        }

        int newIndex = index + delta;
        if (newIndex < 0 || newIndex >= episodes.size()) {
            System.out.println("Não há episódios nessa direção");
            return;
        }

        String nextEpisode = episodes.get(newIndex);
        String path = buildPath(currentSerie, currentSeason, nextEpisode);

        ensureVlcWithMedia(path, currentSerie, currentSeason, nextEpisode);
        play();
    }

    //======== VLC CORE ========

    private static boolean isVlcRunning() {
        return vlcProcess != null && vlcProcess.isAlive();
    }

    public static void startVlc(String path) throws IOException {
        System.out.println("Iniciando o VLC...");

        ProcessBuilder pb = new ProcessBuilder(
                "cvlc",
                "--intf", "rc", // RC = Remote Control; aceite de input em texto
                "--quiet",
                path
        );
        vlcProcess = pb.start();
        vlcWriter = new BufferedWriter(new OutputStreamWriter(vlcProcess.getOutputStream()));
    }

    private static void ensureVlcWithMedia(String path, String serie, String season, String episode) {
        currentSerie = serie;
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

    private static String buildPath(String serie, String season, String episode) {
        return "/media/" + serie + "/" + season + "/" + episode + ".mp4";
    }

    private static boolean isDifferentEpisode(String season, String episode) {
        return !season.equals(currentSeason) || !episode.equals(currentEpisode);
    }

    private static List<String> listEpisodes(String serie, String season){
        String dirPath = "/media/" + serie + "/" + season;
        File dir = new File(dirPath);

        if (!dir.exists() || !dir.isDirectory()){
            System.out.println("Diretório não encontrado: "+ dirPath);
            return List.of();
        }
        return Arrays.stream(dir.listFiles())
                .filter(f -> f.isFile() && f.getName().endsWith(".mp4"))
                .map(f -> f.getName().replace(".mp4", ""))
                .sorted(Comparator.comparingInt(MediaController::extractEpisodeNumber))
                .collect(Collectors.toList());
    }

    private static int extractEpisodeNumber(String episodeName){
        String digits = episodeName.replaceAll("\\D+", "");
        if (digits.isEmpty()) return 0;
        return Integer.parseInt(digits);
    }

}