# APK de pruebas: procedencia y límites

Base: Mudb0y/Apple-Eloquence-ELF, `main`, commit `12cf68ad4aba0841900dafcdd34876101e0913db` (1.3.0), descargado el 12 de octubre de 2026.

El motor procede de la versión 1.3.0 del padre. Esta variante añade una pantalla de pruebas con controles Android estándar, integración CHECK_TTS_DATA/GET_SAMPLE_TEXT y extracción de bibliotecas a nativeLibraryDir. Las pruebas reales requirieron corregir la carga de libeci.so en JNI, acortar las rutas de eci.ini y añadir al convertidor adaptadores para los búferes setjmp/longjmp y las constantes pthread de Darwin frente a Bionic. No se desactivó la protección de pila ni se sustituyó el motor.

CheckVoiceDataActivity y GetSampleTextActivity se adaptaron de la copia local de Free Eloquence TTS, cuyo archivo LICENSE declara MIT, Copyright 2026 Quin Gillespie; se conserva en `TEST-BUILD-LICENSE-FreeEloquence.txt`. El acceso a ajustes sigue su patrón de intentar los ajustes TTS, accesibilidad y ajustes generales. Se mantienen también las condiciones MIT/GPL y avisos del padre, incluido el código GPL-2.0-or-later del puente CJK; los binarios de Apple mantienen sus condiciones originales. Esta adaptación no atribuye el motor a Guillermo ni a Codex.

La versión Android 0.2.0 incorpora los ocho perfiles de `sd_eloquence/src/eci/voices.c`, con sus valores y atribución a Mudb0y / Stas Przecinek. VoiceCatalog.kt y sus pruebas conservan GPL-2.0-or-later. Añade conversión Windows-1252 con normalización NFC, controles accesibles para ocho parámetros y ajustes persistentes por idioma/perfil; el servicio expone 80 combinaciones de voces occidentales a Android. Se conserva la licencia completa en sd_eloquence/LICENSE.GPL. Los extremos de los parámetros y la pronunciación de esta versión requieren verificación audible en el dispositivo.

Las diecinueve bibliotecas Mach-O del padre incluyen únicamente x86_64 y ARM64. No contienen ARMv7/armeabi-v7a. El convertidor sólo define destinos Android para esas arquitecturas de 64 bits. No es posible ofrecer una versión funcional para Android ARM de 32 bits con esta base sin disponer de otro motor compatible o desarrollar una implementación distinta.

La APK de esta rama es ARM64, Android 7 o posterior. x86_64 tiene un fallo de inicialización documentado por el padre y no se incluye. La pantalla permite probar diez idiomas latinos; no añade nuevos modelos, voces, tasas de muestreo ni funciones al motor del padre. La compilación y firma no sustituyen una prueba de síntesis y accesibilidad en un teléfono ARM64.

No seleccionar esta versión experimental como voz del lector de pantalla antes de comprobar Leer texto desde su propia pantalla y conservar otro motor funcional disponible.
