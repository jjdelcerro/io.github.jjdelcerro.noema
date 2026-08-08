package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.impl.AgentAccessControlImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentActionsImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentPathsImpl;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeSourceOfTruth;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileReadToolTest {

    @TempDir
    Path workspaceDir; // Directorio raíz del proyecto simulado

    @TempDir
    Path externalDir;  // Directorio externo

    private Agent agent;
    private AgentSettings settings;
    private AgentActions actions;
    private FileReadTool fileReadTool;

    @BeforeEach
    public void setUp() {
        AgentPaths paths = new AgentPathsImpl(workspaceDir);
        paths.setupHierarchy();

        settings = new AgentSettingsImpl(paths);
        actions = new AgentActionsImpl();

        // Creamos el control de acceso vinculado al workspace temporal
        AgentAccessControl accessControl = new AgentAccessControlImpl(settings, actions, workspaceDir);

        // Agente ligero usando Fakes
        agent = new AgentImpl(null, null, settings, new FakeConsole(), new FakeSourceOfTruth(), accessControl);

        // Herramienta a probar
        fileReadTool = new FileReadTool(agent);
    }

    private void reloadConfig() {
        actions.call(AgentAccessControlImpl.RELOAD_ACTION_NAME, settings);
    }

    @Test
    @DisplayName("Debe leer correctamente un archivo de texto dentro del workspace")
    public void testReadFileSuccess() throws IOException {
        Path file = workspaceDir.resolve("documento.txt");
        String contenido = "Línea 1: Hola Noema\nLínea 2: Prueba de lectura.";
        Files.writeString(file, contenido, StandardCharsets.UTF_8);

        String jsonArgs = "{\"path\": \"" + file.toString().replace("\\", "/") + "\"}";
        String response = fileReadTool.execute(jsonArgs);

        assertTrue(response.contains("STATUS: OK"), "La respuesta debe indicar STATUS: OK");
        assertTrue(response.contains("Hola Noema"), "La respuesta debe contener el texto del archivo");
    }

    @Test
    @DisplayName("Debe devolver ERROR cuando el archivo solicitado no existe")
    public void testReadNonExistentFile() {
        String jsonArgs = "{\"path\": \"no_existo.txt\"}";
        String response = fileReadTool.execute(jsonArgs);

        assertTrue(response.contains("STATUS: ERROR"), "Debe devolver STATUS: ERROR");
        assertTrue(response.contains("Acceso denegado o ruta fuera del sandbox"),
                "Debe indicar que la ruta es inaccesible o no existe");
    }

    @Test
    @DisplayName("Debe rechazar la lectura de un archivo fuera del workspace si no está en allowed_external_paths")
    public void testReadExternalFileDenied() throws IOException {
        Path extFile = externalDir.resolve("archivo_externo.txt");
        Files.writeString(extFile, "Contenido secreto", StandardCharsets.UTF_8);

        String jsonArgs = "{\"path\": \"" + extFile.toString().replace("\\", "/") + "\"}";
        String response = fileReadTool.execute(jsonArgs);

        assertTrue(response.contains("STATUS: ERROR"), "Debe denegar la lectura externa por defecto");
        assertTrue(response.contains("Acceso denegado o ruta fuera del sandbox"));
    }

    @Test
    @DisplayName("Debe permitir leer un archivo externo si su ruta se añade a allowed_external_paths")
    public void testReadExternalFileAllowed() throws IOException {
        Path extFile = externalDir.resolve("archivo_permitido.txt");
        String contenido = "Contenido externo autorizado";
        Files.writeString(extFile, contenido, StandardCharsets.UTF_8);

        // Permitimos el directorio externo en la lista blanca
        settings.setProperty("access_control/allowed_external_paths", List.of(externalDir.toString()));
        reloadConfig();

        String jsonArgs = "{\"path\": \"" + extFile.toString().replace("\\", "/") + "\"}";
        String response = fileReadTool.execute(jsonArgs);

        assertTrue(response.contains("STATUS: OK"), "Debe permitir la lectura tras actualizar la configuración");
        assertTrue(response.contains("Contenido externo autorizado"));
    }

    @Test
    @DisplayName("Debe detectar archivos binarios y denegar su lectura sugiriendo file_extract_text")
    public void testReadBinaryFileDetection() throws IOException {
        Path pngFile = workspaceDir.resolve("imagen.png");
        // Cabecera Mime típica de un archivo PNG de prueba
        byte[] pngHeader = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        Files.write(pngFile, pngHeader);

        String jsonArgs = "{\"path\": \"imagen.png\"}";
        String response = fileReadTool.execute(jsonArgs);

        assertTrue(response.contains("STATUS: ERROR"), "Debe rechazar archivos binarios");
        assertTrue(response.contains("El archivo parece binario"),
                "Debe advertir que el archivo es binario y sugerir usar 'file_extract_text'");
    }
}
