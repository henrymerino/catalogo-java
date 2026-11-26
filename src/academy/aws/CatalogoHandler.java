package academy.aws;

import java.util.Map;

public final class CatalogoHandler {
    private static final Map<String, String> PRODUCTOS = Map.of(
        "1", """
            {"id":1,"nombre":"Teclado","precio":25}
            """.strip(),
        "2", """
            {"id":2,"nombre":"Mouse","precio":15}
            """.strip(),
        "3", """
            {"id":3,"nombre":"Audífonos","precio":35}
            """.strip()
    );

    public Map<String, Object> handleRequest(Map<String, Object> evento) {
        String id = null;
        if (evento != null) {
            if (evento.get("pathParameters") instanceof Map<?, ?> parametros
                    && parametros.get("id") instanceof String idPath) {
                id = idPath;
            }
            if (id == null
                    && evento.get("queryStringParameters") instanceof Map<?, ?> parametros
                    && parametros.get("id") instanceof String idQuery) {
                id = idQuery;
            }
        }

        if (id == null || !id.matches("[0-9]{1,10}")) {
            return respuesta(400, """
                {"error":"El ID debe contener de 1 a 10 dígitos"}
                """.strip());
        }

        String producto = PRODUCTOS.get(id);
        if (producto == null) {
            return respuesta(404, """
                {"error":"Producto no encontrado"}
                """.strip());
        }

        return respuesta(200, producto);
    }

    private static Map<String, Object> respuesta(int estado, String cuerpo) {
        // En una integración proxy, body contiene el JSON en forma de texto.
        return Map.of(
            "statusCode", estado,
            "headers", Map.of("Content-Type", "application/json; charset=utf-8"),
            "isBase64Encoded", false,
            "body", cuerpo
        );
    }
}
