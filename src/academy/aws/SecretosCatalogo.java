package academy.aws;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class SecretosCatalogo {
    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private SecretosCatalogo() {}

    static boolean esConsultaDeEstado(Map<String, Object> evento) {
        return evento != null
                && "GET /catalogo/estado-integracion".equals(evento.get("routeKey"));
    }

    static boolean verificarAcceso() {
        String arn = System.getenv("CATALOGO_SECRET_ARN");
        String token = System.getenv("AWS_SESSION_TOKEN");
        if (arn == null || arn.isBlank() || token == null || token.isBlank()) {
            System.err.println("catalogo_secret configuracion_incompleta");
            return false;
        }

        String id = URLEncoder.encode(arn, StandardCharsets.UTF_8);
        var solicitud = HttpRequest.newBuilder(
                        URI.create("http://localhost:2773/secretsmanager/get?secretId=" + id))
                .header("X-Aws-Parameters-Secrets-Token", token)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            // El estado HTTP basta para el diagnóstico. El cuerpo contiene el secreto.
            var respuesta = CLIENTE.send(solicitud, HttpResponse.BodyHandlers.discarding());
            System.out.println("catalogo_secret extension_http=" + respuesta.statusCode());
            return respuesta.statusCode() == 200;
        } catch (IOException error) {
            System.err.println("catalogo_secret extension_no_disponible");
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("catalogo_secret solicitud_interrumpida");
            return false;
        }
    }
}
