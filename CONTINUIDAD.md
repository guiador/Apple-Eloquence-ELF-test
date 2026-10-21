# Continuidad — Apple-Eloquence-ELF-test

Actualizado el 20 de octubre de 2026. Conversión experimental del motor Eloquence y adaptador TTS Android ARM64.

## Punto de partida

Repositorio de trabajo: https://github.com/guiador/Apple-Eloquence-ELF-test (public). Se conserva el historial y la procedencia; esta organización no convierte los componentes de terceros en código propio.

macho2elf: conversión; sd_eloquence: integración; android: servicio/JNI; vendor: recursos con sus condiciones originales.

## Construcción y comprobación

Consultar CMakeLists.txt, android/README.md y TEST-BUILD-NOTICES.md. La construcción requiere las versiones NDK y bibliotecas indicadas allí.

tools/verify.sh y pruebas de android/. No confundir la evidencia del convertidor en Linux con una prueba del APK Android.

Comprobaciones ejecutadas durante la preparación de continuidad:

- APROBADA: `python.exe tools/verify_checksums.py` (1.7 s).
  Resultado: PASS 19 vendored-file SHA-256 checks; no synthesis or Android device validation.

Las comprobaciones automáticas describen sólo lo ejecutado. No certifican accesibilidad humana, funcionamiento de todos los dispositivos ni equivalencia entre un paquete anterior y el código actual. La evidencia histórica conserva su fecha y versión.

## Entrega y entorno local

En el equipo de trabajo, el índice único es `Codex/Entregas/Apple Eloquence`. `Codex/Entregas/LEEME.md` y sus manifiestos identifican los paquetes conservados y sus SHA-256. Las entregas anteriores a esta revisión no deben presentarse como compilaciones de este commit. Los paquetes y entornos no forman parte del código fuente.

Las herramientas locales se reúnen en `Codex/Soporte/Herramientas`; los enlaces de compatibilidad conservan las rutas de los scripts. En otro equipo se deben preparar las dependencias indicadas por este proyecto. No copiar ciegamente rutas de Guillermo ni tratar un entorno virtual Windows como un instalador portable.

Las claves de firma y configuraciones privadas se conservan localmente y se excluyen de Git. No se sustituyen para crear una actualización; usar las claves existentes o documentar una nueva identidad de paquete. No incorporar cuentas, bases, fotos ni documentos de usuarios a las pruebas.

## Próximo trabajo útil

Verificar el APK en ARM64 y revisar la cancelación y el contrato TTS. Conservar los avisos de los binarios; la licencia del convertidor no relicencia las voces.

Antes de editar, leer el alcance vigente y revisar `git status`. Mantener separados cambios de producto, construcción y limpieza. Conservar las APIs, formatos y licencias salvo que la tarea requiera cambiarlos y se pueda verificar la migración. Las instrucciones posteriores del usuario prevalecen sobre esta nota.

## Referencias

- [README.md](README.md)
- [android/README.md](android/README.md)
- [TEST-BUILD-NOTICES.md](TEST-BUILD-NOTICES.md)
