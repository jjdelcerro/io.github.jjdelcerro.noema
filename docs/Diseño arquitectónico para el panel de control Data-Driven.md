
# Diseño Arquitectónico: Panel de Control "Data-Driven"

## 1. El Concepto: Separación de Planos
El diseño separa el **Plano de Datos** (la conversación y la lógica del agente) del **Plano de Control** (la configuración y los parámetros del motor). Esta separación permite realizar "Hot-Swapping" (cambio en caliente) de modelos, APIs y umbrales de memoria sin reiniciar la sesión ni perder el estado del diálogo.

## 2. Los Tres Pilares Técnicos

### A. El Árbol de Menús (Estructura JSON)
La interfaz no se programa de forma rígida, sino que se define mediante un árbol jerárquico. Cada nodo del árbol puede ser una **Rama** (navegación) o una **Hoja** (acción o entrada de datos).

**Ejemplo de Definición:**
```json
{
  "menu": [
    {
      "label": "Configuración de LLM",
      "variable": "LLM_PROVIDER",
      "action": "CHANGE_PROVIDER",
      "nodes": [
        {
          "label": "OpenRouter",
          "nodes": [
            {
              "label": "API Key",
              "type": "INPUT_STRING",
              "variable": "LLM_API_KEY",
              "action": "REBUILD_MODEL"
            },
            {
              "label": "Selección de Modelo",
              "variable": "LLM_MODEL",
              "action": "REBUILD_MODEL",
              "options": [
                { "label": "Llama 3.3 70B", "value": "meta-llama/llama-3.3-70b-instruct:free" },
                { "label": "GLM-4.5-Air", "value": "z-ai/glm-4.5-air:free" },
                { "label": "DeepSeek R1", "value": "tngtech/deepseek-r1t2-chimera:free" }
              ]
            }
          ]
        }
      ]
    },
    {
      "label": "Gestión de Memoria",
      "nodes": [
        {
          "label": "Umbral de Compactación",
          "type": "INPUT_INT",
          "variable": "COMPACTION_THRESHOLD",
          "action": "UPDATE_PARAMS"
        },
        {
          "label": "Forzar Compactación Ahora",
          "action": "FORCE_COMPACTION"
        }
      ]
    }
  ]
}
```

### B. El Mapa de Variables (Estado)
Un `Map<String, String>` actúa como la "Fuente de la Verdad" del estado actual.
*   **Persistencia:** Se vuelca a un fichero `.properties` estándar al cerrar y se carga al iniciar.
*   **Soberanía:** Permite que el programa recuerde que el usuario prefiere "DeepSeek" aunque se haya descargado un nuevo JAR.

### C. El Mapa de Acciones (Lógica)
Un `Map<String, Runnable>` que vincula los identificadores del JSON con código Java ejecutable.
*   Permite que la UI (consola o Swing) no sepa **qué** hace una acción, solo que debe disparar un "trigger".
*   Facilita el cambio de comportamiento: la misma acción `REBUILD_MODEL` puede cerrar conexiones antiguas y abrir nuevas de forma transparente.

---

## 3. Estrategia de Implementación de Interfaz

### Fase 1: Consola (Navegación Numérica)
Se implementa un bucle sencillo que recorre el árbol JSON:
1.  Muestra el `label` del nodo actual y sus hijos numerados.
2.  El usuario introduce un número para descender o `0` para subir.
3.  Si el tipo es `INPUT_STRING`, captura la entrada del teclado, actualiza la variable y lanza el `Runnable` asociado.

### Fase 2: Swing (Jerarquía Visual)
La migración es directa gracias al desacoplo:
*   **Izquierda (`JTree`):** Se pobla automáticamente recorriendo el JSON.
*   **Derecha (`JPanel` dinámico):** Al seleccionar un nodo, un "Renderer" decide qué mostrar:
    *   Si hay `options`: Un `JComboBox` o una lista de `JRadioButton`.
    *   Si es `INPUT_STRING`: Un `JTextField` con un botón "Aplicar".
    *   Si es una acción pura: Un `JButton` de ejecución.

---

## 4. ¿Por qué hacerlo así? (Justificación Técnica)

1.  **Independencia de la Vista:** Permite evolucionar el agente en la consola (donde la velocidad de desarrollo es mayor) y "regalarle" la interfaz gráfica más adelante sin tocar una sola línea de lógica de negocio.
2.  **Extensibilidad:** Añadir un nuevo modelo o un nuevo parámetro de configuración es tan fácil como añadir 4 líneas al JSON y, si acaso, un nuevo `Runnable`. No requiere recompilar grandes estructuras de la UI.
3.  **Filosofía Desktop:** Mantiene la promesa de un sistema autónomo. Al persistir en `.properties` y no depender de servicios externos para la configuración, el usuario tiene el control total sobre los "termostatos" de su IA.
4.  **Hot-Swapping:** Es la única forma de comparar modelos de forma científica. Puedes estar en medio de una duda compleja, cambiar a un modelo con más razonamiento (Reasoning Model), obtener la respuesta y volver al modelo rápido para seguir la charla.

---

**Nota para el futuro:** Este diseño elimina la "incomodidad" de la consola al sacar la configuración del flujo de chat, permitiendo que el diálogo sea diálogo y la gestión sea gestión. Al ser *Data-Driven*, el `Main.java` se vuelve mucho más limpio y fácil de mantener.
