import org.eclipse.paho.client.mqttv3.*;

import java.io.IOException;
import java.util.UUID;

public class MediaController {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TOPIC = "/media/advtime/#";

    private static Process vlcProcess;

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

        String videoPath = "/media/advtime/" + season + "/" + episode + "/video.mp4";

        switch (action) {
            case "play" -> playVideo(videoPath);
            case "pause" -> pauseVideo();
            default -> System.out.println("Ação desconhecida: " + action);
        }
    }

    private static void playVideo(String path) {
        try {
            if (vlcProcess != null && vlcProcess.isAlive()) {
                System.out.println("Vídeo já está em execução");
                return;
            }

            System.out.println("Iniciando vídeo: " + path);

            ProcessBuilder pb = new ProcessBuilder(
                    "vlc",
                    "--intf", "dummy",
                    "--play-and-exit",
                    path
            );

            pb.inheritIO();
            vlcProcess = pb.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void pauseVideo() {
        if (vlcProcess == null || !vlcProcess.isAlive()) {
            System.out.println("Nenhum vídeo em execução");
            return;
        }

        try {
            System.out.println("Pausando vídeo");
            Runtime.getRuntime().exec("pkill -STOP vlc");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}