package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;
import static org.junit.jupiter.api.Assertions.*;

public class AgentAccessControlTest {

    @TempDir
    Path workspaceDir; // Directorio raíz del proyecto (Sandbox)

    @TempDir
    Path externalDir;  // Directorio fuera del proyecto (Extranjero)

    private AgentSettings settings;
    private AgentActions actions;
    private AgentAccessControl accessControl;

    @BeforeEach
    public void setUp() {
        AgentPaths paths = new AgentPathsImpl(workspaceDir);
        paths.setupHierarchy();

        settings = new AgentSettingsImpl(paths);
        actions = new AgentActionsImpl();

        // Configuramos permisos base por defecto
        settings.setProperty("access_control/allow_disk_write", "true");
        settings.setProperty("access_control/allow_shell_execution", "false");
        settings.setProperty("access_control/allow_internet_access", "false");

        accessControl = new AgentAccessControlImpl(settings, actions, workspaceDir);
    }

    private void reloadAccessControl() {
        // Notificamos la acción para recargar la configuración del control de acceso
        actions.call(AgentAccessControlImpl.RELOAD_ACTION_NAME, settings);
    }

    @Test
    @DisplayName("Debe permitir lectura y escritura de archivos dentro del workspace")
    public void testAccessInsideWorkspace() throws IOException {
        Path localFile = workspaceDir.resolve("src/Main.java");
        Files.createDirectories(localFile.getParent());
        Files.createFile(localFile);

        // Lectura
        Path readResolved = accessControl.resolvePathOrNull("src/Main.java", PATH_ACCESS_READ);
        assertNotNull(readResolved, "Debe permitir la lectura de archivos en el workspace");
        assertEquals(localFile.toRealPath(), readResolved);

        // Escritura
        Path writeResolved = accessControl.resolvePathOrNull("src/Main.java", PATH_ACCESS_WRITE);
        assertNotNull(writeResolved, "Debe permitir la escritura si allow_disk_write es true");
    }

    @Test
    @DisplayName("Debe denegar lectura de archivos externos NO incluidos en allowed_external_paths")
    public void testExternalFileDeniedByDefault() throws IOException {
        Path extFile = externalDir.resolve("ext_doc.txt");
        Files.createFile(extFile);

        Path resolved = accessControl.resolvePathOrNull(extFile.toString(), PATH_ACCESS_READ);
        assertNull(resolved, "Debe denegar el acceso a archivos fuera del sandbox si no están en la lista blanca");
    }

    @Test
    @DisplayName("Debe permitir lectura de archivos externos añadidos a allowed_external_paths")
    public void testExternalFileAllowedInWhitelist() throws IOException {
        Path extFile = externalDir.resolve("ext_doc.txt");
        Files.createFile(extFile);

        // Añadimos el directorio externo a la lista blanca de la configuración
        settings.setProperty("access_control/allowed_external_paths", List.of(externalDir.toString()));
        reloadAccessControl();

        Path resolved = accessControl.resolvePathOrNull(extFile.toString(), PATH_ACCESS_READ);
        assertNotNull(resolved, "Debe permitir la lectura si el directorio está en allowed_external_paths");
        assertEquals(extFile.toRealPath(), resolved);
    }

    @Test
    @DisplayName("nom_writable_paths debe permitir LECTURA pero denegar ESCRITURA")
    public void testNomWritablePathsBehavior() throws IOException {
        Path readOnlyFile = workspaceDir.resolve("readonly_config.json");
        Files.createFile(readOnlyFile);

        // Añadimos el archivo a la lista de 'rutas no escribibles' (solo lectura)
        settings.setProperty("access_control/nom_writable_paths", List.of(readOnlyFile.toString()));
        reloadAccessControl();

        // 1. Lectura: DEBE PERMITIRSE
        Path readResolved = accessControl.resolvePathOrNull(readOnlyFile.toString(), PATH_ACCESS_READ);
        assertNotNull(readResolved, "nom_writable_paths debe permitir la LECTURA de archivos");

        // 2. Escritura: DEBE SER DENEGADA
        Path writeResolved = accessControl.resolvePathOrNull(readOnlyFile.toString(), PATH_ACCESS_WRITE);
        assertNull(writeResolved, "nom_writable_paths debe DENEGAR la ESCRITURA");
    }

    @Test
    @DisplayName("nom_readable_paths debe denegar tanto LECTURA como ESCRITURA")
    public void testNomReadablePathsBehavior() throws IOException {
        Path secretFile = workspaceDir.resolve("secret.key");
        Files.createFile(secretFile);

        // Añadimos el archivo a la lista negra de lectura
        settings.setProperty("access_control/nom_readable_paths", List.of(secretFile.toString()));
        reloadAccessControl();

        // Lectura denegada
        Path readResolved = accessControl.resolvePathOrNull(secretFile.toString(), PATH_ACCESS_READ);
        assertNull(readResolved, "nom_readable_paths debe DENEGAR la lectura");

        // Escritura denegada
        Path writeResolved = accessControl.resolvePathOrNull(secretFile.toString(), PATH_ACCESS_WRITE);
        assertNull(writeResolved, "nom_readable_paths debe DENEGAR la escritura");
    }

    @Test
    @DisplayName("Archivos que no existen físicamente en disco deben devolver null")
    public void testNonExistentFileReturnsNull() {
        Path nonExistent = workspaceDir.resolve("archivo_fantasma.txt");

        Path resolved = accessControl.resolvePathOrNull(nonExistent.toString(), PATH_ACCESS_READ);
        assertNull(resolved, "Si el archivo no existe en disco, toRealPath() falla y debe retornar null");
    }
}
