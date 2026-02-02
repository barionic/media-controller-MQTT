import org.eclipse.paho.client.mqttv3.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.UUID;

public class MediaController {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TOPIC = "/media/advtime/#";

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

        String videoPath = "/media/advtime/" + season + "/" + episode + ".mp4";

        switch (action) {
            case "play" -> play(videoPath);
            case "pause" -> pause();
            default -> System.out.println("Ação desconhecida: " + action);
        }
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

    private static void play(String path) {
        try {
            if (vlcProcess == null || !vlcProcess.isAlive()) {
                startVlc(path);
            }else{
                sendVlcCommand("play");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void pause() {
        if (vlcProcess == null || !vlcProcess.isAlive()) {
            System.out.println("Nenhum vídeo em execução");
            return;
        }
        sendVlcCommand("pause");
    }

}