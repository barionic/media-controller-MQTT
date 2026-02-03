import org.eclipse.paho.client.mqttv3.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.UUID;

public class MediaController {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TOPIC = "/media/advtime/#";

    //Lista de eps fixo por enquanto, para termos ordenação entre eps
    private static final String[] EPISODES = {"ep18", "ep22"};

    private static String currentSeason;
    private static String currentEpisode;

    private static String buildPath(String season, String episode){
        return "/media/advtime/" + season + "/" + episode + ".mp4";
    }

    private static boolean isVlcRunning() {
        return vlcProcess != null && vlcProcess.isAlive();
    }

    private static Process vlcProcess;
    private static BufferedWriter vlcWriter;

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

        System.out.println("Aguardando comandos MQTT...");
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
            case "play" -> play(videoPath, season, episode);
            case "pause" -> pause();
            case "begin" -> begin();
            case "next" -> changeEpisode(+1);
            case "prev" -> changeEpisode(-1);
            case "exit" -> exitProgram();
            default -> System.out.println("Ação desconhecida: " + action);
        }
    }

    //======== EPISODE NAVIGATION ========

    private static void changeEpisode(int delta){
        if (currentEpisode == null){
            System.out.println("Nenhum episódio em execução");
            return;
        }

        int index = indexOfEpisodes(currentEpisode);
        int newIndex = index + delta;

        if (newIndex<0 || newIndex >= EPISODES.length){
            System.out.println("Não há episódios nessa direção");
            return;
        }

        String nextEpisode = EPISODES[newIndex];
        String path = buildPath(currentSeason, nextEpisode);

        loadAndPlay(path, currentSeason, nextEpisode);
    }

    private static int indexOfEpisodes(String ep){
        for (int i=0; i<EPISODES.length; i++){
            if (EPISODES[i].equals(ep)){return i;}
        }
        return -1;
    }

    //======== VLC CONTROL METHODS ========

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

    private static void sendVlcCommand(String cmd){
        try{
            System.out.println("VLC CMD: " + cmd);
            vlcWriter.write(cmd);
            vlcWriter.newLine();
            vlcWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void play(String path, String season, String episode) {
        if (!isVlcRunning()) {
            try{
                startVlc(path); //playInicial
                currentSeason = season;
                currentEpisode = episode;
            } catch (IOException e){
                e.printStackTrace();
            }
        }else {
            sendVlcCommand("play");
        }
    }

    private static void pause() {
        //if (vlcProcess == null || !vlcProcess.isAlive()) {
        if (!isVlcRunning()) {
            System.out.println("Nenhum vídeo em execução");
            return;
        }
        sendVlcCommand("pause");
    }

    private static void stop(){
        //if(vlcProcess != null && vlcProcess.isAlive()) {
        if(isVlcRunning()) {
            sendVlcCommand("stop");
        }
    }

    private static void loadAndPlay(String path, String season, String episode){
        currentSeason = season;
        currentEpisode = episode;

        //if (vlcProcess == null || !vlcProcess.isAlive()){
        if (!isVlcRunning()){
            try {
                startVlc(path);
            } catch (IOException e){
                e.printStackTrace();
            }
            return;
        }
        sendVlcCommand("clear");
        sendVlcCommand("add " + path);
        sendVlcCommand("play");
    }

    private static void begin(){
        //if (vlcProcess == null || !vlcProcess.isAlive()) {
        if (!isVlcRunning()) {
            System.out.println("Nenhum vídeo em execução");
            return;
        }
        sendVlcCommand("seek 0");
        sendVlcCommand("play");
    }

    private static void exitProgram(){
        System.out.println("Encerrando MediaController...");
        stop();
        System.exit(0);
    }

}