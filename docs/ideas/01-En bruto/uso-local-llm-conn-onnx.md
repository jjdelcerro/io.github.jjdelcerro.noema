https://huggingface.co/onnx-community/Qwen3.5-0.8B-ONNX

Visto así, **la estrategia es impecable**. 

La limitación que te hizo descartar `inference4j` fue su capa de alto nivel (`OnnxTextGenerator.qwen2()`, `smolLM2()`), que efectivamente es un envoltorio rígido y acoplado a dos modelos concretos.

Si usas `io.github.inference4j:onnxruntime-genai` únicamente como el **proveedor del empaquetado nativo** (el JAR en Maven Central que ya te resuelve los `.so` y los bindings de JNI), pero **ignoras sus clases de alto nivel** y programas contra la API oficial de Microsoft (`ai.onnxruntime.genai.*`), ganas total libertad.

---

### ¿Cómo funcionaría en la práctica?

A la API de Microsoft (`ai.onnxruntime.genai.*`) **no le importa el nombre del modelo** (le da igual si es Qwen, Llama, Phi, Gemma o SmolLM). Solo espera la ruta a una carpeta que contenga los ficheros estándar de ONNX GenAI (`model.onnx`, `genai_config.json`, `tokenizer.json`).

#### 1. Dependencia en `pom.xml`
En lugar de `inference4j-core`, puedes apuntar directamente al binding nativo:

```xml
<dependency>
  <groupId>io.github.inference4j</groupId>
  <artifactId>onnxruntime-genai</artifactId>
  <version>0.10.0</version>
</dependency>
```

*(Como en tu script de arranque `./noema` ya tienes `--enable-native-access=ALL-UNNAMED`, el runtime nativo de Java 25 cargará sin problemas).*

#### 2. Código universal para cualquier modelo local
Puedes instanciar y ejecutar **cualquier modelo** descargado de `onnx-community/*` simplemente pasándole el directorio:

```java
import ai.onnxruntime.genai.SimpleGenAI;
import ai.onnxruntime.genai.GeneratorParams;
import java.nio.file.Path;

public class LocalOnnxChatEngine {

    private final SimpleGenAI model;

    public LocalOnnxChatEngine(Path modelFolder) {
        // Carga model.onnx, weights, tokenizer y genai_config.json del directorio
        this.model = new SimpleGenAI(modelFolder.toAbsolutePath().toString());
    }

    public String generate(String prompt, int maxTokens, double temperature) {
        GeneratorParams params = model.createGeneratorParams(prompt);
        params.setSearchOption("max_length", maxTokens);
        params.setSearchOption("temperature", temperature);
        
        // Ejecución autorregresiva completa gestionada por C++/ONNX
        return model.generate(params, null); 
    }
}
```

O si necesitas **streaming** token a token para conectarlo con la consola de Noema o el callback de LangChain4j, la misma API te da el control detallado:

```java
import ai.onnxruntime.genai.*;

public void generateStream(String prompt, java.util.function.Consumer<String> tokenConsumer) {
    try (Tokenizer tokenizer = model.createTokenizer();
         TokenizerStream stream = tokenizer.createStream();
         GeneratorParams params = model.createGeneratorParams()) {

        params.setInput(tokenizer.encode(prompt));
        params.setSearchOption("max_length", 256);

        try (Generator generator = new Generator(model, params)) {
            while (!generator.isDone()) {
                generator.computeLogits();
                generator.generateNextToken();
                
                int nextToken = generator.getLastTokenInSequence();
                String textChunk = stream.decode(nextToken);
                if (textChunk != null && !textChunk.isEmpty()) {
                    tokenConsumer.accept(textChunk);
                }
            }
        }
    }
}
```

---

### ¿Qué ganas con este enfoque?

1. **Desacoplamiento total:** Si mañana sale `Qwen3.5-0.8B`, solo tienes que cambiar la carpeta del modelo en disco; el código Java no cambia ni una sola línea.
2. **Cero dolores de compilación C++:** Te ahorras compilar con CMake y arrastrar binarios a mano.
3. **Rendimiento C++ / Cero sobrecarga en el Heap:** El bucle de inferencia, la memoria de tensores y el KV-Cache se ejecutan en memoria nativa fuera del recolector de basura de Java.
4. **Encaje natural en Noema:** Te permite crear una implementación ligera de `Agent.ChatModel` (por ejemplo `OnnxGenAiChatModel`) que puedes enchufar donde quieras (en `LlmModule` de Groovy, en un subagente rápido, o para clasificar anotaciones en segundo plano).

El único requisito práctico es que el modelo de Hugging Face que descargues (`onnx-community/Qwen3-0.6B-ONNX` o similar) traiga el fichero `genai_config.json`, que es el estándar que usan todas las exportaciones de esa organización.
