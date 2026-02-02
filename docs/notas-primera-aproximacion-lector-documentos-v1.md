Como arquitecto, te diré que esa idea es la culminación lógica del proceso de optimización que empezamos. Si el **CSV de entrada** redujo tokens y mejoró la "vista" del modelo, pedirle un **CSV de salida** (una lista de líneas con 7 columnas) es el movimiento maestro para cerrar el círculo de eficiencia.

### ¿Es demasiado pedirle 7 columnas?
**No, al contrario.** Los LLMs (y especialmente DeepSeek o GPT-4o) trabajan mejor cuando les das una "rejilla" mental. Al obligarle a rellenar columnas específicas, estás activando su modo de **Procesamiento Estructurado** y desactivando su modo de **Chatterbox**.

En el mundo de Don Julián, esto es como darle al empleado una hoja de Excel con las columnas ya rotuladas en lugar de un cuaderno en blanco.

---

### Propuesta de las 7 Columnas para el `DocMapper` (Pasada 1)

Para que el mapa sea útil desde la primera pasada, estas serían las columnas ideales:

1.  **`lineStart`**: El ID físico (puntero).
2.  **`level`**: Profundidad jerárquica (1, 2, 3...).
3.  **`title`**: El texto del encabezado (normalizado).
4.  **`parentTitle`**: Para reconstruir el árbol en Java fácilmente (usa "ROOT" si no tiene).
5.  **`type`**: Clasificación rápida (`SECTION`, `ABSTRACT`, `ANNEX`, `REFERENCES`).
6.  **`hasVisualBreak`**: Un flag (0 o 1) si el modelo detecta que hay un cambio de página o salto visual brusco (útil para el post-procesado).
7.  **`isNumbered`**: Un flag (0 o 1) si el título venía numerado originalmente (ej: "1.1").

### El Prompt actualizado (Salida Tabular)

```markdown
# PROPÓSITO
Extraer metadatos estructurales en formato tabular (CSV).

# TAREAS
1. Identifica los encabezados reales.
2. Ignora el ruido (índices, pies de página).

# FORMATO DE SALIDA (CSV ESTRICTO)
Devuelve exclusivamente líneas CSV sin cabecera, usando el carácter pipe (|) como separador para evitar conflictos con comas en los títulos.

Columnas:
lineStart | level | title | parentTitle | type | hasVisualBreak | isNumbered

# EJEMPLO DE SALIDA
45|1|Abstract|ROOT|ABSTRACT|1|0
230|1|1 Introduction|ROOT|SECTION|1|1
280|2|1.1 Background|1 Introduction|SECTION|0|1
```

---

### Ventajas de esta aproximación "Flat"

1.  **Token Efficiency Extrema**: Te ahorras todas las claves del JSON (`"title":`, `"lineStart":`, etc.) en cada sección. En un documento con 100 secciones, esto son miles de tokens ahorrados en la salida (que es la parte más lenta y cara).
2.  **Parsing "Don Julián" (Robusto y simple)**:
    En Java 21, procesar esto es una delicia. No necesitas librerías pesadas de JSON si no quieres. Con un `String.split("\\|")` y un `Stream`, tienes el mapa cargado.
3.  **Depuración visual**: Puedes leer el resultado de DeepSeek de un vistazo. Si ves una línea que no cuadra, el error salta a la vista inmediatamente.

### La implementación en Java 21

Usando **Records**, tu lógica de recepción quedaría muy limpia:

```java
public record DocNode(
    int lineStart, 
    int level, 
    String title, 
    String parentTitle, 
    String type,
    boolean hasVisualBreak,
    boolean isNumbered
) {}

// En tu DocMapper.java
List<DocNode> nodes = response.lines()
    .map(line -> line.split("\\|"))
    .map(parts -> new DocNode(
        Integer.parseInt(parts[0].trim()),
        Integer.parseInt(parts[1].trim()),
        parts[2].trim(),
        parts[3].trim(),
        parts[4].trim(),
        "1".equals(parts[5].trim()),
        "1".equals(parts[6].trim())
    ))
    .toList();
```

**Un pequeño aviso de arquitecto:**
A veces, los LLMs se emocionan y te ponen una línea de texto antes o después del CSV ("Aquí tienes el resultado..."). Asegúrate de que tu código Java ignore cualquier línea que no empiece por un número o que no contenga el separador `|`.

¿Te parece que este formato de "Tabla de 7 columnas" es lo suficientemente sólido para empezar a picar la lógica de los `Virtual Threads` para los resúmenes?