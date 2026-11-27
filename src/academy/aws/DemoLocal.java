package academy.aws;

import java.util.Map;

public final class DemoLocal {
   /* public static void main(String[] args) {
        var handler = new CatalogoHandler();
        String[] ids = args.length == 0 ? new String[]{"1", "99", "abc"} : args;

        for (String id : ids) {
            var evento = Map.<String, Object>of(
                "routeKey", "GET /productos/{id}",
                "pathParameters", Map.of("id", id)
            );
            var respuesta = handler.handleRequest(evento);
            System.out.printf("GET /productos/%s → HTTP %s%n%s%n%n",
                id, respuesta.get("statusCode"), respuesta.get("body"));
        }
    }*/

    public static void main(String[] args) {
        var handler = new CatalogoHandler();

        var post = Map.<String, Object>of(
                "requestContext", Map.of("http", Map.of("method", "POST")),
                "body", "{\"id\":41,\"nombre\":\"Monitor\",\"precio\":180}"
        );
        mostrar("POST", handler.handleRequest(post));

        var put = Map.<String, Object>of(
                "requestContext", Map.of("http", Map.of("method", "PUT")),
                "queryStringParameters", Map.of("id", "41"),
                "body", "{\"nombre\":\"Monitor HD\",\"precio\":195}"
        );
        mostrar("PUT", handler.handleRequest(put));

        var get = Map.<String, Object>of(
                "requestContext", Map.of("http", Map.of("method", "GET")),
                "queryStringParameters", Map.of("id", "40")
        );
        mostrar("GET por ID", handler.handleRequest(get));
    }

    private static void mostrar(String metodo, Map<String, Object> respuesta) {
        System.out.printf("%s → HTTP %s%n%s%n%n",
                metodo, respuesta.get("statusCode"), respuesta.get("body"));
    }
}
