package io.github.jjdelcerro.noema.main;

import io.github.inference4j.nlp.OnnxTextGenerator;
import java.time.LocalDateTime;

/**
 *
 * @author jjdelcerro
 */
public class Test {
    public static void main(String[] args) {
        String prompt1 = 
"""
Resume el siguiente contenido en UN SOLO párrafo, manteniendo las ideas principales y los puntos clave.

El resumen debe ser directo, sin frases introductorias como "La sección describe" o "En esta sección se...", o "El documento describe..." y sin mencionar el título de la sección.
No uses tags html en el resumen.
Devuelve únicamente el párrafo resumen.

Contenido:
La tabla
ARENA2_INFORMES
actúa como el contenedor lógico de cada envío de información.

<ul><li>Campos Relevantes:
Código único del informe, fechas de inicio y fin del periodo de exportación de los datos.
</li>
<li>Función:
Permite gestionar las actualizaciones masivas y evitar la duplicidad de datos al procesar diferentes lotes temporales (quincenas o meses).
</li>
</ul>
""";
        String prompt2 = 
"""
Resume el siguiente contenido en UN SOLO párrafo, manteniendo las ideas principales y los puntos clave. 

El resumen debe ser directo, sin frases introductorias como "La sección describe" o "En esta sección se...", o "El documento describe..." y sin mencionar el título de la sección. 
No uses tags html en el resumen.
Devuelve únicamente el párrafo resumen.

Contenido:
Bajo cada registro de la tabla principal de accidentes, se vinculan mediante relaciones de integridad las siguientes tablas que detallan de forma pormenorizada a los participantes y medios involucrados:
<ul><li>ARENA2_VEHICULOS
: Registra la información técnica y administrativa de cada unidad de transporte involucrada en el siniestro.
</li>
<li><ul><li>Datos clave
: Incluye el tipo de vehículo, marca, modelo, nacionalidad de la matrícula, y el estado de vigencia de la ITV y del seguro.
</li>
<li>Mercancías Peligrosas
: Contiene campos específicos para identificar si el vehículo transportaba sustancias peligrosas, incluyendo el panel naranja y el código internacional
ONU
.
</li>
<li>Circunstancias
: Almacena si el vehículo se incendió, si se dio a la fuga o si presentaba anomalías previas (frenos, neumáticos, etc.).
</li>
</ul></li>
<li>ARENA2_CONDUCTORES
: Almacena la información del usuario que operaba el vehículo en el momento del suceso.
</li>
<li><ul><li>Perfil
: Fecha de nacimiento, sexo y nacionalidad.
</li>
<li>Control y Pruebas
: Resultados detallados de las pruebas de alcohol (tasa en aire o sangre) y de drogas (tipología de sustancia detectada y confirmación).
</li>
<li>Seguridad e Infracciones
: Uso de accesorios de seguridad (casco, cinturón), tipo de permiso de conducir y presuntas infracciones cometidas vinculadas a la conducta o a la velocidad.
</li>
</ul></li>
<li>ARENA2_PASAJEROS
: Contiene los datos de todas las personas que viajaban en los vehículos implicados sin ejercer funciones de conducción.
</li>
<li><ul><li>Ubicación
: Detalla la posición exacta en el vehículo (asiento delantero, trasero izquierdo, central, etc.).
</li>
<li>Seguridad Infantil
: Incluye indicadores específicos sobre el uso de Sistemas de Retención Infantil (SRI) o si el menor viajaba en brazos de un adulto.
</li>
<li>Estado
: Datos demográficos y gravedad de las lesiones sufridas.
</li>
</ul></li>
<li>ARENA2_PEATONES
: Registra la información de las personas que se encontraban en la vía o sus inmediaciones y resultaron afectadas.
</li>
<li><ul><li>Acciones
: Almacena la maniobra que realizaba el peatón (cruzando por paso señalizado, irrumpiendo en la calzada, trabajando en la vía, etc.).
</li>
<li>Responsabilidad
: Incluye indicadores de presuntas infracciones del peatón y su posible responsabilidad en el desencadenamiento del accidente.
</li>
<li>Pruebas
: Al igual que los conductores, permite registrar los resultados de las pruebas de detección de sustancias.
</li>
</ul></li>
</ul>
Todas estas tablas se encuentran vinculadas a la tabla central mediante el campo de enlace del accidente, asegurando que toda la información personal y técnica sea accesible desde la ficha del siniestro principal.
""";
//        OnnxTextGenerator.Builder genbuilder = OnnxTextGenerator.smolLM2();
        OnnxTextGenerator.Builder genbuilder = OnnxTextGenerator.qwen2();
        OnnxTextGenerator gen = genbuilder.maxNewTokens(50).temperature(0.8f).topK(50).build();
        System.out.println(LocalDateTime.now().toString());
        gen.generate(prompt1, token -> System.out.print(token));
        System.out.println();
        System.out.println(LocalDateTime.now().toString());
        gen.generate(prompt2, token -> System.out.print(token));
        System.out.println();
        System.out.println(LocalDateTime.now().toString());
    }
 
}
