Ver de utilizar la libreria inference4j para invocar a modelos pequeños locales desde java y utilizarlos para hacer resumenes.

https://github.com/inference4j/inference4j

https://huggingface.co/inference4j/qwen2.5-1.5b-instruct

try (var gen = OnnxTextGenerator.qwen2().build()) {
    GenerationResult result = gen.generate("What is Java?");
    System.out.println(result.text());
}

Utilizar docling a traves de docker para generar el xml.

docker run quay.io/docling-project/docling-serve 

```
curl -X 'POST' \
  'http://localhost:5001/v1/convert/source' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "sources": [{"kind": "http", "url": "https://arxiv.org/pdf/2501.17887"}]
}'
```
Este ejemplo procesa un PDF desde una URL. La respuesta te devolverá el contenido extraído en formato estructurado.


Recorrerlo, extrayendo los titulos, niveles, numeros de linea y utilizar OnnxTextGenerator.qwen2 para hacer resumenes por titulo. Para ello utilizar un prompt tal que asi:
```
Resume el siguiente contenido en UN SOLO párrafo, manteniendo las ideas principales y los puntos clave. 

El resumen debe ser directo, sin frases introductorias como "La sección describe" o "En esta sección se...", o "El documento describe..." y sin mencionar el título de la sección. 
No uses tags html en el resumen.
Devuelve únicamente el párrafo resumen.

Contenido:
```


---



### 🛠️ Opción 1: ONNX Runtime GenAI (La más sencilla)

Esta es una extensión oficial de Microsoft pensada específicamente para simplificar la generación de texto con LLMs. Está diseñada para ser fácil de usar y maneja gran parte de la complejidad por ti.

*   **Ventaja**: Su API es de muy alto nivel. Cargas el modelo y el tokenizador, y la propia librería se encarga del bucle de generación.
*   **Formato de modelo**: Está preparada para trabajar con la estructura de archivos que has visto en el repositorio de Hugging Face, que incluye `model.onnx`, `model.onnx_data`, `tokenizer.json`, etc..

Un ejemplo de cómo sería el código en Java es muy conciso:

```java
// 1. Cargar el modelo desde el directorio donde está
var model = new Model("la/ruta/a/tu/modelo/qwen3.5-0.8b-text-onnx/");

// 2. Crear los parámetros de generación
var params = new GeneratorParams(model);
params.setMaxLength(100);
params.setTemperature(0.7f);

// 3. Generar el texto
var generator = new Generator(model, params);
var output = generator.generate("Explain gravity");
System.out.println(output);
```

**Nota**: Esta API está en fase "preview" y podría cambiar en el futuro, pero es una opción muy prometedora y la que mejor se ajusta a lo que buscas.

### 🧰 Opción 2: Deep Java Library (DJL) + ONNX Runtime

DJL es un framework de deep learning para Java creado por Amazon. Actúa como una capa de abstracción que te permite usar diferentes motores (como ONNX Runtime, PyTorch, TensorFlow) con una misma API unificada.

*   **Ventaja**: Es una solución más madura y con una comunidad activa. Hay ejemplos completos y tutoriales para desplegar modelos como Qwen 3.5 con Java y DJL.
*   **Curva de aprendizaje**: Es un poco más compleja que la opción anterior, pero te da mucho más control y flexibilidad si planeas hacer más cosas con IA en el futuro.

### ⚙️ Opción 3: ONNX Runtime (API base)

Esta es la opción de más bajo nivel. Usarías directamente la librería `onnxruntime` para Java.

*   **Ventaja**: Tienes el control total sobre cada paso del proceso.
*   **Desventaja**: Es la más compleja. Tendrías que encargarte de **todo** manualmente: cargar el modelo, tokenizar el texto de entrada, ejecutar el modelo iterativamente para generar cada nuevo token, decodificar la salida y manejar las condiciones de parada. Requiere un conocimiento profundo de cómo funcionan los modelos de lenguaje.

### 🚀 ¿Cómo empezar con la Opción 1 (ONNX Runtime GenAI)?

1.  **Prepara el modelo**: Asegúrate de tener los archivos del modelo en un directorio. Puedes descargarlos directamente desde Hugging Face usando `git lfs` o la interfaz web. La estructura que has visto en `onnx-community/Qwen3.5-0.8B-Text-ONNX` es perfecta para esta librería.
2.  **Añade la dependencia**: En tu `pom.xml`, añade la dependencia de `onnxruntime-genai`. La versión exacta puede variar, pero busca el artefacto en el repositorio de Maven.
3.  **Escribe el código**: Con solo unas pocas líneas como las del ejemplo, ya estarías generando texto.

### ⚠️ Un par de cosas a tener en cuenta

*   **Rendimiento**: El modelo Qwen3.5-0.8B es pequeño, pero ten en cuenta que en CPU puede ser más lento que en GPU. Para un uso de prueba, es aceptable.
*   **Memoria**: El modelo ocupa unos 2-3 GB en disco. Asegúrate de tener suficiente memoria RAM para cargarlo.
*   **Soporte**: Si encuentras problemas, la comunidad de ONNX Runtime y DJL es bastante activa en GitHub y foros.

En resumen, **usar ONNX Runtime directamente en Java para cargar Qwen3.5 es una excelente idea y una ruta muy práctica**. Te recomiendo empezar por la **Opción 1 (ONNX Runtime GenAI)** por su simplicidad y porque está diseñada exactamente para este propósito.

---

### 🚀 Cómo implementarlo en Java: La ruta recomendada

Para integrar Qwen3.5-0.8B con ONNX Runtime en Java, la opción más sencilla y directa es usar la extensión oficial **`onnxruntime-genai`** de Microsoft.

Esta librería está diseñada específicamente para simplificar la generación de texto con LLMs y **ya soporta la arquitectura Qwen**.

**Aquí tienes un ejemplo práctico de cómo quedaría tu código:**

```java
import ai.onnxruntime.genai.*;

public class QwenChat {
    public static void main(String[] args) {
        // 1. Cargar el modelo desde el directorio donde lo hayas descargado
        // (por ejemplo, desde onnx-community/Qwen3.5-0.8B-Text-ONNX)
        String modelPath = "/ruta/a/tu/modelo/qwen3.5-0.8b-text-onnx/";
        try (Model model = new Model(modelPath)) {
            
            // 2. Crear los parámetros de generación
            try (GeneratorParams params = new GeneratorParams(model)) {
                params.setMaxLength(100);
                params.setTemperature(0.7f);
                // Puedes ajustar más parámetros aquí: top_p, top_k, etc.
                
                // 3. Generar el texto
                try (Generator generator = new Generator(model, params)) {
                    String prompt = "Explain gravity";
                    generator.appendTokenSequences(prompt); // Tokeniza y añade el prompt
                    
                    System.out.print("Respuesta: ");
                    while (!generator.isDone()) {
                        generator.computeLogits();
                        generator.generateNextToken();
                        // Obtener y mostrar el token generado
                        var sequence = generator.getSequence(0);
                        System.out.print(sequence.getLastTokenText());
                    }
                    System.out.println();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

**Pasos para ponerlo en marcha:**

1.  **Añade la dependencia** a tu `pom.xml` (o `build.gradle`):
    ```xml
    <dependency>
        <groupId>com.microsoft.onnxruntime</groupId>
        <artifactId>onnxruntime-genai</artifactId>
        <version>0.4.0</version> <!-- O la última versión disponible -->
    </dependency>
    ```

2.  **Descarga el modelo** desde Hugging Face: `onnx-community/Qwen3.5-0.8B-Text-ONNX`. Puedes hacerlo manualmente o con `git lfs`.

3.  **Apunta a la ruta** del modelo en tu código y ¡ya está!



