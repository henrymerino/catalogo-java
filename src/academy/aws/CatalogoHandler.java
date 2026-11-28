package academy.aws;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CatalogoHandler {
    private static final Map<String, Producto> PRODUCTOS = Map.of(
        "1", new Producto("1", "Teclado", new BigDecimal("25")),
        "2", new Producto("2", "Mouse", new BigDecimal("15")),
        "3", new Producto("3", "Audífonos", new BigDecimal("35"))
    );

    public Map<String, Object> handleRequest(Map<String, Object> evento) {
       /* if (SecretosCatalogo.esConsultaDeEstado(evento)) {
            return SecretosCatalogo.verificarAcceso()
                    ? respuesta(200, "{\"integracion\":\"lista\"}")
                    : respuesta(503, "{\"integracion\":\"no_disponible\"}");
        }*/

        String metodo = obtenerMetodo(evento);
        try {
            return switch (metodo) {
                case "GET" -> consultar(evento);
                case "POST" -> crear(evento);
                case "PUT" -> actualizar(evento);
                default -> respuesta(405, "{\"error\":\"Método no permitido\"}");
            };
        } catch (IOException e) {
            System.err.println("No se pudo acceder al archivo del catálogo: " + e.getMessage());
            return respuesta(500, "{\"error\":\"No se pudo acceder al catálogo\"}");
        }
    }

    private static Map<String, Object> consultar(Map<String, Object> evento) throws IOException {
        IdEvento idEvento = obtenerId(evento);
        if (idEvento.invalido()) {
            return errorId();
        }

        Map<String, Producto> guardados = leerProductosGuardados();
        if (!idEvento.proporcionado()) {
            Map<String, Producto> catalogo = new LinkedHashMap<>(PRODUCTOS);
            catalogo.putAll(guardados);
            String cuerpo = catalogo.values().stream()
                .map(CatalogoHandler::productoJson)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            return respuesta(200, cuerpo);
        }

        if (!idValido(idEvento.id())) {
            return errorId();
        }
        Producto producto = guardados.get(idEvento.id());
        if (producto == null) {
            producto = PRODUCTOS.get(idEvento.id());
        }
        if (producto == null) {
            return respuesta(404, "{\"error\":\"Producto no encontrado\"}");
        }
        return respuesta(200, productoJson(producto));
    }

    private static Map<String, Object> crear(Map<String, Object> evento) throws IOException {
        Map<String, Object> cuerpo;
        try {
            cuerpo = leerCuerpo(evento);
            String id = leerIdProducto(cuerpo.get("id"));
            validarNombreYPrecio(cuerpo);

            Map<String, Producto> guardados = leerProductosGuardados();
            if (PRODUCTOS.containsKey(id) || guardados.containsKey(id)) {
                return respuesta(409, "{\"error\":\"El ID ya existe\"}");
            }

            Producto producto = new Producto(id, (String) cuerpo.get("nombre"),
                (BigDecimal) cuerpo.get("precio"));
            guardados.put(id, producto);
            escribirProductosGuardados(guardados);
            return respuesta(201, productoJson(producto));
        } catch (IllegalArgumentException e) {
            return respuesta(400, "{\"error\":\"El ID, el nombre o el precio no son válidos\"}");
        }
    }

    private static Map<String, Object> actualizar(Map<String, Object> evento) throws IOException {
        IdEvento idEvento = obtenerId(evento);
        if (idEvento.invalido() || !idValido(idEvento.id())) {
            return errorId();
        }

        try {
            Map<String, Object> cuerpo = leerCuerpo(evento);
            validarNombreYPrecio(cuerpo);
            Map<String, Producto> guardados = leerProductosGuardados();
            if (!guardados.containsKey(idEvento.id())) {
                return respuesta(404, "{\"error\":\"Producto no encontrado en productos.txt\"}");
            }

            Producto actualizado = new Producto(idEvento.id(), (String) cuerpo.get("nombre"),
                (BigDecimal) cuerpo.get("precio"));
            guardados.put(idEvento.id(), actualizado);
            escribirProductosGuardados(guardados);
            return respuesta(200, productoJson(actualizado));
        } catch (IllegalArgumentException e) {
            return respuesta(400, "{\"error\":\"El nombre o el precio no son válidos\"}");
        }
    }

    private static Map<String, Object> leerCuerpo(Map<String, Object> evento) {
        if (evento == null || !(evento.get("body") instanceof String texto)) {
            throw new IllegalArgumentException("El cuerpo debe ser texto JSON");
        }
        Object valor = new JsonParser(texto).parse();
        if (!(valor instanceof Map<?, ?> mapa)) {
            throw new IllegalArgumentException("El cuerpo debe ser un objeto JSON");
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
            resultado.put((String) entrada.getKey(), entrada.getValue());
        }
        return resultado;
    }

    private static void validarNombreYPrecio(Map<String, Object> cuerpo) {
        Object nombre = cuerpo.get("nombre");
        Object precio = cuerpo.get("precio");
        if (!(nombre instanceof String texto) || texto.isBlank()
                || !(precio instanceof BigDecimal valor) || valor.signum() < 0) {
            throw new IllegalArgumentException("Nombre o precio inválido");
        }
    }

    private static String leerIdProducto(Object valor) {
        String id;
        if (valor instanceof String texto) {
            id = texto;
        } else if (valor instanceof BigDecimal numero) {
            try {
                id = numero.toBigIntegerExact().toString();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("El ID debe ser entero", e);
            }
        } else {
            throw new IllegalArgumentException("Falta el ID");
        }
        if (!idValido(id)) {
            throw new IllegalArgumentException("El ID debe contener de 1 a 10 dígitos");
        }
        return id;
    }

    private static IdEvento obtenerId(Map<String, Object> evento) {
        if (evento == null) {
            return new IdEvento(null, false, false);
        }
        Object pathParameters = evento.get("pathParameters");
        if (pathParameters instanceof Map<?, ?> parametros && parametros.containsKey("id")) {
            Object valor = parametros.get("id");
            return new IdEvento(valor instanceof String texto ? texto : null, true,
                !(valor instanceof String));
        }
        Object queryParameters = evento.get("queryStringParameters");
        if (queryParameters instanceof Map<?, ?> parametros && parametros.containsKey("id")) {
            Object valor = parametros.get("id");
            return new IdEvento(valor instanceof String texto ? texto : null, true,
                !(valor instanceof String));
        }
        return new IdEvento(null, false, false);
    }

    private static String obtenerMetodo(Map<String, Object> evento) {
        if (evento == null) {
            return "GET";
        }
        Object requestContext = evento.get("requestContext");
        if (requestContext instanceof Map<?, ?> contexto
                && contexto.get("http") instanceof Map<?, ?> http
                && http.get("method") instanceof String metodo) {
            return metodo.toUpperCase(java.util.Locale.ROOT);
        }
        if (evento.get("httpMethod") instanceof String metodo) {
            return metodo.toUpperCase(java.util.Locale.ROOT);
        }
        if (evento.get("routeKey") instanceof String ruta) {
            int separador = ruta.indexOf(' ');
            if (separador > 0) {
                return ruta.substring(0, separador).toUpperCase(java.util.Locale.ROOT);
            }
        }
        return "GET";
    }

    private static Map<String, Producto> leerProductosGuardados() throws IOException {
        Map<String, Producto> productos = new LinkedHashMap<>();
        Path archivo = archivoProductos();
        if (!Files.exists(archivo)) {
            return productos;
        }

        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).isBlank()) {
                continue;
            }
            try {
                Map<String, Object> campos = leerObjetoJson(lineas.get(i));
                String id = leerIdProducto(campos.get("id"));
                validarNombreYPrecio(campos);
                productos.put(id, new Producto(id, (String) campos.get("nombre"),
                    (BigDecimal) campos.get("precio")));
            } catch (IllegalArgumentException e) {
                throw new IOException("Registro inválido en productos.txt, línea " + (i + 1), e);
            }
        }
        return productos;
    }

    private static Map<String, Object> leerObjetoJson(String texto) {
        Object valor = new JsonParser(texto).parse();
        if (!(valor instanceof Map<?, ?> mapa)) {
            throw new IllegalArgumentException("El registro debe ser un objeto");
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
            resultado.put((String) entrada.getKey(), entrada.getValue());
        }
        return resultado;
    }

    private static void escribirProductosGuardados(Map<String, Producto> productos)
            throws IOException {
        Path archivo = archivoProductos();
        Path directorio = archivo.toAbsolutePath().getParent();
        Files.createDirectories(directorio);
        Path temporal = Files.createTempFile(directorio, "productos-", ".tmp");
        try {
            List<String> lineas = productos.values().stream()
                .map(CatalogoHandler::productoJson)
                .toList();
            Files.write(temporal, lineas, StandardCharsets.UTF_8);
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static Path archivoProductos() {
        String ruta = System.getenv("PRODUCTOS_FILE_PATH");
        return Path.of(ruta == null || ruta.isBlank() ? "productos.txt" : ruta);
    }

    private static boolean idValido(String id) {
        return id != null && id.matches("[0-9]{1,10}");
    }

    private static Map<String, Object> errorId() {
        return respuesta(400, "{\"error\":\"El ID debe contener de 1 a 10 dígitos\"}");
    }

    private static String productoJson(Producto producto) {
        return "{\"id\":" + producto.id()
            + ",\"nombre\":\"" + escaparJson(producto.nombre())
            + "\",\"precio\":" + producto.precio().toString() + "}";
    }

    private static String escaparJson(String texto) {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char caracter = texto.charAt(i);
            switch (caracter) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (caracter < 0x20) {
                        json.append(String.format("\\u%04x", (int) caracter));
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }
        return json.toString();
    }

    private static Map<String, Object> respuesta(int estado, String cuerpo) {
        return Map.of(
            "statusCode", estado,
            "headers", Map.of("Content-Type", "application/json; charset=utf-8"),
            "isBase64Encoded", false,
            "body", cuerpo
        );
    }

    private record Producto(String id, String nombre, BigDecimal precio) {
    }

    private record IdEvento(String id, boolean proporcionado, boolean invalido) {
    }

    private static final class JsonParser {
        private final String texto;
        private int posicion;

        private JsonParser(String texto) {
            this.texto = texto;
        }

        private Object parse() {
            Object valor = leerValor();
            espacios();
            if (posicion != texto.length()) {
                throw error("Contenido extra después del JSON");
            }
            return valor;
        }

        private Object leerValor() {
            espacios();
            if (posicion >= texto.length()) {
                throw error("Falta un valor JSON");
            }
            return switch (texto.charAt(posicion)) {
                case '{' -> leerObjeto();
                case '[' -> leerArreglo();
                case '"' -> leerCadena();
                case 't' -> leerLiteral("true", Boolean.TRUE);
                case 'f' -> leerLiteral("false", Boolean.FALSE);
                case 'n' -> leerLiteral("null", null);
                default -> leerNumero();
            };
        }

        private Map<String, Object> leerObjeto() {
            posicion++;
            Map<String, Object> objeto = new LinkedHashMap<>();
            espacios();
            if (consumir('}')) {
                return objeto;
            }
            while (true) {
                espacios();
                if (posicion >= texto.length() || texto.charAt(posicion) != '"') {
                    throw error("La clave del objeto debe ser texto");
                }
                String clave = leerCadena();
                espacios();
                exigir(':');
                Object valor = leerValor();
                if (objeto.containsKey(clave)) {
                    throw error("Clave JSON duplicada");
                }
                objeto.put(clave, valor);
                espacios();
                if (consumir('}')) {
                    return objeto;
                }
                exigir(',');
            }
        }

        private List<Object> leerArreglo() {
            posicion++;
            List<Object> arreglo = new ArrayList<>();
            espacios();
            if (consumir(']')) {
                return arreglo;
            }
            while (true) {
                arreglo.add(leerValor());
                espacios();
                if (consumir(']')) {
                    return arreglo;
                }
                exigir(',');
            }
        }

        private String leerCadena() {
            exigir('"');
            StringBuilder resultado = new StringBuilder();
            while (posicion < texto.length()) {
                char caracter = texto.charAt(posicion++);
                if (caracter == '"') {
                    return resultado.toString();
                }
                if (caracter == '\\') {
                    if (posicion >= texto.length()) {
                        throw error("Escape JSON incompleto");
                    }
                    char escape = texto.charAt(posicion++);
                    switch (escape) {
                        case '"', '\\', '/' -> resultado.append(escape);
                        case 'b' -> resultado.append('\b');
                        case 'f' -> resultado.append('\f');
                        case 'n' -> resultado.append('\n');
                        case 'r' -> resultado.append('\r');
                        case 't' -> resultado.append('\t');
                        case 'u' -> resultado.append(leerUnicode());
                        default -> throw error("Escape JSON no válido");
                    }
                } else if (caracter < 0x20) {
                    throw error("Carácter de control no escapado");
                } else {
                    resultado.append(caracter);
                }
            }
            throw error("Cadena JSON sin cerrar");
        }

        private char leerUnicode() {
            if (posicion + 4 > texto.length()) {
                throw error("Escape Unicode incompleto");
            }
            try {
                char valor = (char) Integer.parseInt(texto.substring(posicion, posicion + 4), 16);
                posicion += 4;
                return valor;
            } catch (NumberFormatException e) {
                throw error("Escape Unicode no válido");
            }
        }

        private Object leerNumero() {
            int inicio = posicion;
            consumir('-');
            if (consumir('0')) {
                if (posicion < texto.length() && Character.isDigit(texto.charAt(posicion))) {
                    throw error("Cero inicial no permitido");
                }
            } else {
                exigirDigitos();
            }
            if (consumir('.')) {
                exigirDigitos();
            }
            if (consumir('e') || consumir('E')) {
                if (!consumir('+')) {
                    consumir('-');
                }
                exigirDigitos();
            }
            try {
                return new BigDecimal(texto.substring(inicio, posicion));
            } catch (NumberFormatException e) {
                throw error("Número JSON no válido");
            }
        }

        private Object leerLiteral(String literal, Object valor) {
            if (!texto.startsWith(literal, posicion)) {
                throw error("Valor JSON no válido");
            }
            posicion += literal.length();
            return valor;
        }

        private void exigirDigitos() {
            int inicio = posicion;
            while (posicion < texto.length() && Character.isDigit(texto.charAt(posicion))) {
                posicion++;
            }
            if (inicio == posicion) {
                throw error("Se esperaba un dígito");
            }
        }

        private void espacios() {
            while (posicion < texto.length()
                    && " \t\n\r".indexOf(texto.charAt(posicion)) >= 0) {
                posicion++;
            }
        }

        private boolean consumir(char esperado) {
            if (posicion < texto.length() && texto.charAt(posicion) == esperado) {
                posicion++;
                return true;
            }
            return false;
        }

        private void exigir(char esperado) {
            if (!consumir(esperado)) {
                throw error("Se esperaba '" + esperado + "'");
            }
        }

        private IllegalArgumentException error(String mensaje) {
            return new IllegalArgumentException(mensaje + " en la posición " + posicion);
        }
    }
}
