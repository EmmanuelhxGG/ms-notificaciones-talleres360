# Talleres360 — Microservicio de notificaciones

## Recuperación, DLQ y comprobante de texto

Órdenes conserva los comandos en su outbox PostgreSQL hasta recibir confirmación
de RabbitMQ. El consumidor guarda el comando antes del ACK y deduplica por
`commandId`. Una redelivery no crea otra notificación. El envío SMTP usa un
`Message-ID` estable para rastrear reintentos; eso no convierte SMTP en entrega
exactamente una vez ante una caída posterior al envío y anterior al commit.

JSON inválido o procesamiento rechazado sin reencolar pasa a su DLQ. Los fallos
definitivos de envío también se publican a la DLQ con confirmación y outbox en
PostgreSQL (`falloPublicado`, migración V5). Las caídas temporales del proveedor
mantienen `REINTENTO`; una caída de RabbitMQ mantiene pendiente la publicación.
No se envía a DLQ por eliminar productos, purgar colas o eliminar una cola.

Al entregar se genera un comando `COMPROBANTE` en `q.cmd.invoice`: texto UTF-8
con productos, cantidades y total. Se ve en Notificaciones y se descarga como
`.txt`; no genera otro correo ni PDF. Es un comprobante interno, no tributario.

## Actualización: JSON directo y bandejas (28-10-2026)

Se admite desde RabbitMQ un JSON con solo `email`, `asunto` y `mensaje` en la cola
de correo. No requiere pedido: se persiste con `orderId=null` y se muestra en
Mensajes generales. Véase [publicación desde RabbitMQ](MENSAJES_DESDE_RABBITMQ.md)
para copiar el JSON y configurar routing key, persistencia y deduplicación.

Todos los roles tienen bandeja personal filtrada por correo confiable del BFF.
`ambito=personal` es el valor predeterminado; `ambito=operativo` exige Operador.
`GET /api/notifications?generales=true` devuelve solo avisos sin pedido del ámbito
autorizado. La agrupación paginada cuenta también el bloque general sin omitirlo.
La migración PostgreSQL necesaria para una base existente está explicada en la guía.


Servicio independiente que recibe comandos de notificación, genera tickets de taller y envía correos mediante SMTP. Mantiene su propia base de datos y no modifica órdenes ni stock.

Para visualizar los correos de laboratorio sin enviarlos a Internet, utiliza
[Mailpit y su acceso mediante túnel SSH](CORREOS_MAILPIT.md). La configuración
`compose.mailpit.yml` captura los avisos en un buzón web persistente. No entrega
mensajes a Gmail, Outlook ni cuentas institucionales.

Documentación actualizada el **22 de octubre de 2026**. Repositorio independiente,
rama `emmanuel-noti`, puerto **8084**. Atiende el flujo de Talleres360, un único
taller. Los ejemplos contienen marcadores y datos ficticios, nunca credenciales
SMTP, IP reales, IDs de cuenta o identidades privadas.

La infraestructura RabbitMQ independiente está en [infra/mq](infra/mq/README.md).
Con `RABBIT_ENABLED=true` consume tres colas durables: correo, ticket de taller y
comprobante de entrega. Las solicitudes nuevas usan correo y ticket; al entregar
se genera un comprobante de texto, sin PDF.
Usa ACK/NACK manual. Recibe `Message` y deserializa su cuerpo con su propio
`ObjectMapper`, sin confiar en el tipo Java del productor. Confirma con
`basicAck` únicamente después del commit de la bandeja durable. Los datos
inválidos reciben `basicNack(requeue=false)`; los errores de persistencia se
reintentan tres veces con espera exponencial antes de pasar a DLQ.
Con `RABBIT_DLQ_AUTO_STARTUP=true` (opcional), las tres DLQ se archivan en
`mensajes_fallidos`, se registran en logs y se confirman manualmente después del
commit. Si falla ese archivo, se reencolan sin borrar el mensaje. Con `false`
se conservan visibles en RabbitMQ para una inspección manual (predeterminado). El comprobante de servicio no reemplaza
una boleta/factura del SII y se descarga mediante
`GET /api/notifications/{id}/receipt`, previa autorización del BFF y del servicio.

El perfil PostgreSQL incorpora Flyway: adopta la base previa como versión 1 y
ejecuta `db/migration/postgresql/V2__admitir_comprobantes.sql` para ampliar el CHECK
de tipos sin borrar datos. V3 conserva una asociación histórica de comprobantes;
el flujo nuevo no la utiliza. V4 amplía el mensaje para el detalle del carrito.
H2 y Oracle no ejecutan esas migraciones PostgreSQL.

El destinatario es el correo asociado al cliente autenticado. El remitente es una
cuenta del administrador designada por Talleres360 y autorizada por su proveedor:
no se utilizan contraseñas de
clientes ni se presume que un usuario Azure tenga buzón. Los correos se envían
como texto UTF-8 sin adjuntos, sin esperar ni generar PDF. Los documentos históricos
ya guardados se conservan; no se reenvían automáticamente correos ya enviados.
SMTP permanece desactivado hasta configurar la cuenta remitente. STARTTLS es
obligatorio por defecto y se verifica la identidad del servidor TLS.

Las solicitudes nuevas generan tres tipos de correo: aceptación (productos,
cantidades y monto), lista para retirar/entregar (aviso de disponibilidad) y rechazo
(motivo obligatorio). Crear o marcar entregada no genera un cuarto correo.
RabbitMQ confirma el comando después de persistirlo; el worker SMTP conserva en
base el trabajo pendiente y lo reintenta después de caídas. No promete entrega
exactamente una vez por SMTP: una caída después del envío y antes del commit puede
repetirlo. Revisar los avisos históricos antes de activar el remitente real.

Los fallos temporales del proveedor se reintentan sin límite de intentos, con
espera exponencial limitada a una hora. No se abandonan por una caída prolongada.
Credenciales rechazadas, direcciones inválidas o contenido mal formado quedan
FALLIDA para corregirlos y solicitar un reintento administrativo.

## Tecnologías

Java 21, Spring Boot 3.5.6, Spring Web, Spring Data JPA, Bean Validation, Lombok, Spring AMQP, JavaMail y OpenAPI. PostgreSQL en Compose, H2 en memoria para desarrollo y pruebas. También incluye el controlador JDBC y un perfil de conexión Oracle.

## Estructura

```text
src/main/java/com/talleres360/notificaciones/
├── controller/    API HTTP: recibir, consultar y solicitar reintentos.
├── dto/           Contratos de entrada, salida y paginación.
├── model/         Entidad persistente y tipos de notificación.
├── repository/    Consultas y bloqueo transaccional de registros.
├── service/       Validación, deduplicación, procesamiento y planificación.
├── messaging/     Consumidor RabbitMQ.
└── config/        Credencial interna, errores y documentación OpenAPI.
src/main/resources/application.yml
Dockerfile         Construcción por etapas; ejecución con usuario no root.
compose.yml        Aplicación y PostgreSQL con volumen independiente.
.env.example       Variables sin credenciales reales.
```

## Funcionamiento

1. Se recibe un comando JSON por HTTP interno o RabbitMQ.
2. Se valida y persiste antes de responder. `commandId` es un UUID: repetir el mismo comando no crea otra notificación. Reutilizarlo con contenido distinto se rechaza.
3. Un proceso periódico selecciona hasta 50 notificaciones y bloquea cada registro durante su procesamiento.
4. Un ticket termina en `GENERADA`; su contenido queda disponible para consulta. Un correo termina en `ENVIADA` únicamente cuando SMTP acepta el envío.
5. Los fallos temporales SMTP generan reintentos con espera creciente, sin abandonar por cantidad de intentos. Los errores definitivos requieren corrección y reintento administrativo.

Con `SMTP_ENABLED=false`, los correos permanecen `PENDIENTE` sin consumir intentos y no bloquean los tickets. SMTP no garantiza entrega en la bandeja del destinatario. Si el proveedor acepta un correo y el proceso cae antes de guardar ese resultado, un reintento puede duplicar el envío: no se afirma entrega exactamente una vez.

La clave primaria evita sobrescribir un comando en una carrera concurrente. Si dos primeras inserciones coinciden, HTTP puede responder `409`; el emisor debe repetir el mismo UUID y contenido. Por RabbitMQ se aplican los reintentos configurados.

## API

Puerto predeterminado: **8084**.

| Método | Ruta | Función |
| --- | --- | --- |
| POST | `/internal/commands` | Persistir comando. `202` significa recibido, no correo enviado. |
| GET | `/api/notifications` | Consultar con `orderId`, `page` y `size` opcionales. |
| POST | `/api/notifications/{id}/retry` | Reintentar un registro `FALLIDA`; solo Admin. |
| GET | `/actuator/health` | Estado del servicio, sin detalles internos. |
| GET | `/swagger-ui/index.html` | Documentación interactiva. |
| GET | `/v3/api-docs` | Especificación OpenAPI. |

Las operaciones de negocio requieren `X-Internal-Key`. La consulta requiere `X-Actor-Role`: `Operador` consulta solo tickets; `Cliente` consulta únicamente correos cuyo destinatario coincide con `X-Customer-Email`, obtenido del token por el BFF. Sin identidad del cliente la consulta se rechaza. Admin consulta únicamente su bandeja personal por esta API. El reintento Admin es una operación interna de mantenimiento y no tiene ruta pública en el BFF. El tamaño máximo de página es 100 y la numeración comienza en 0.

La respuesta paginada tiene `content`, `page`, `size`, `totalElements` y `totalPages`. No se exponen entidades JPA directamente.

Ejemplo de comando HTTP o mensaje RabbitMQ. Sustituye el marcador por un UUID
válido nuevo para cada tarea; no es una credencial:

```json
{
  "commandId": "<UUID_DEL_COMANDO>",
  "orderId": 15,
  "tipo": "CORREO",
  "destinatario": "cliente@ejemplo.cl",
  "asunto": "Tu orden fue aceptada",
  "mensaje": "La orden 15 fue aceptada por el taller.",
  "occurredAt": "2026-10-08T15:30:00Z"
}
```

Para un ticket usar `"tipo": "TICKET_TALLER"`; el destinatario puede ser `null`. El ticket se relaciona con la orden por `orderId`, no es una orden adicional. El mismo `commandId` se conserva en todos los reintentos del emisor.

## Ejecución local

Requisitos: JDK 21 y conexión para descargar dependencias en la primera ejecución. El repositorio incluye Maven Wrapper.

En PowerShell, desde la carpeta del proyecto:

```powershell
$env:INTERNAL_API_KEY='<CLAVE_INTERNA_PRIVADA>'
.\mvnw.cmd '-DskipTests' package
.\mvnw.cmd spring-boot:run
```

Sustituye el marcador antes de ejecutar. En Linux/macOS, usar `./mvnw` en lugar de
`.\mvnw.cmd` y exportar la variable. El perfil `local` usa H2: los datos se pierden
al detener la aplicación. Maven no carga `.env` automáticamente; ese archivo lo
utiliza Compose. Para probar solo HTTP sin broker, usa `RABBIT_ENABLED=false`.

## Docker Compose independiente

1. Copiar `.env.example` como `.env`.
2. Completar `DB_PASSWORD` e `INTERNAL_API_KEY` con valores propios; mantener `DB_USERNAME=talleres360` o elegir otro usuario.
3. Ejecutar:

```sh
docker compose up -d --build
```

La imagen se construye desde este repositorio como `talleres360/ms-notificaciones:local`. PostgreSQL no publica su puerto al host. La aplicación espera a que la base esté disponible y ambos contenedores usan `restart: unless-stopped`; esto requiere que el motor Docker arranque con el equipo. El volumen `talleres360-notificaciones_datos_postgres` conserva los datos.

La configuración privada se guarda en **`.env` en la raíz de este repositorio**,
junto a `compose.yml`. Plantilla resumida para conexión con los dos nodos:

```dotenv
DB_USERNAME=<USUARIO_BASE_NOTIFICACIONES>
DB_PASSWORD=<CONTRASENA_PRIVADA_BASE>
INTERNAL_API_KEY=<CLAVE_INTERNA_COMPARTIDA>
RABBIT_ENABLED=true
RABBIT_ADDRESSES=<HOST_PRIVADO_RABBIT>:5672,<HOST_PRIVADO_RABBIT>:5673
RABBIT_USERNAME=<USUARIO_RABBIT_AUTORIZADO>
RABBIT_PASSWORD=<CONTRASENA_PRIVADA_RABBIT>
RABBIT_VHOST=/
RABBIT_DLQ_AUTO_STARTUP=false
SMTP_ENABLED=false
SMTP_HOST=<HOST_SMTP_DEL_PROVEEDOR>
SMTP_PORT=587
SMTP_USERNAME=<CUENTA_AUTORIZADA_DEL_ADMIN>
SMTP_PASSWORD=<CREDENCIAL_PRIVADA_DEL_PROVEEDOR>
SMTP_FROM=<REMITENTE_AUTORIZADO_DEL_ADMIN>
SMTP_AUTH=true
SMTP_STARTTLS=true
```

Sustituye los marcadores y ajusta las opciones al proveedor autorizado. Habilita
`SMTP_ENABLED=true` únicamente después de comprobar el remitente, autenticación,
conexión TLS y avisos pendientes. El correo del cliente es el **destinatario**;
su contraseña nunca se solicita. El rol Admin de la aplicación no concede permisos
SMTP. Las credenciales del remitente solo residen en el servidor de Notificaciones.

## Configuración

| Variables | Uso |
| --- | --- |
| `SERVER_PORT` | Puerto de la aplicación; 8084 por defecto. Compose utiliza 8084. |
| `INTERNAL_API_KEY` | Credencial de servicio. Debe coincidir con la del emisor autorizado. Sin clave, las operaciones protegidas se rechazan. |
| `SPRING_PROFILES_ACTIVE` | `local`, `postgres` u `oracle`; Compose selecciona `postgres`. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión externa al ejecutar con `postgres` u `oracle`. Compose configura la URL de su PostgreSQL. |
| `SMTP_ENABLED` | Activar envíos reales. Desactivado por defecto. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | Proveedor de correo y remitente autorizado. |
| `SMTP_AUTH`, `SMTP_STARTTLS` | Autenticación y STARTTLS; ambos activados por defecto. |
| `RABBIT_ENABLED` | Activar las tres colas. `.env.example` lo configura en `true`. |
| `RABBIT_DLQ_AUTO_STARTUP` | `false` conserva DLQ para inspección manual; `true` archiva y retira esos fallos de las colas. |
| `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USERNAME`, `RABBIT_PASSWORD`, `RABBIT_VHOST` | Conexión al broker. |
| `RABBIT_ADDRESSES` | Lista `host:puerto` separada por comas; tiene prioridad sobre host/puerto individuales. Sin prefijo HTTP. |
| `app.rabbit.topologia` | Contrato central en `config/TopologiaRabbit.java`: exchanges, colas, rutas y patrones. |
| `RABBIT_RETRY_ATTEMPTS`, `RABBIT_RETRY_WAIT_MS` | Tres intentos y espera inicial de 1000 ms. |
| `NOTIFICATIONS_POLL_MS` | Intervalo del proceso periódico, 3000 ms por defecto al ejecutar Java. |
| `DDL_AUTO` | Gestión de esquema; `update` para este laboratorio. |

Los chequeos SMTP/Rabbit del endpoint de salud se activan con sus respectivas integraciones. Las opciones avanzadas de Spring pueden configurarse mediante variables de entorno adicionales.

El perfil Oracle usa `DB_URL=jdbc:oracle:thin:@//HOST:1521/SERVICIO`, usuario y
contraseña propios. No cambia el Compose PostgreSQL ni convierte datos existentes.
Los chequeos locales con H2 no verifican una instalación Oracle real.

## RabbitMQ y conexión con el resto del sistema

El consumidor procesa JSON desde las tres colas y confirma después de persistir. Rechaza mensajes malformados o mayores de 64 KiB; reintenta fallos transitorios de base de datos tres veces. La topología DLX/DLQ se configura en `infra/mq`. Fallos SMTP posteriores se reintentan en la base de Notificaciones, no reencolando indefinidamente el comando.

La entrada HTTP interna permite probarlo sin RabbitMQ. No sustituye la integración de mensajería exigida por el caso. El servicio no inventa destinatarios ni recibe tokens Azure del navegador: el BFF valida al usuario y debe colocar los encabezados internos confiables.

API Gateway conserva al BFF como destino. Órdenes usa `MESSAGING_TRANSPORT=rabbit`
y conserva UUID y contenido en el outbox. `http` es una alternativa explícita,
nunca simultánea. En BFF configura
`NOTIFICATIONS_URL=http://<IP_PRIVADA_NOTIFICACIONES>:8084`; Órdenes mantiene la
misma URL para la alternativa HTTP. El frontend ofrece tickets al Operador y
avisos propios al Cliente. Este servicio no necesita IDs de Entra ni Kafka.

### Conservación ante caídas

Si cae RabbitMQ, el outbox de Órdenes mantiene los comandos sin confirmar. Si cae
Notificaciones, los mensajes permanecen en las colas durables. Después del ACK,
la tarea está en PostgreSQL y el proceso SMTP puede retomarla sin depender de que
el mensaje siga en Rabbit. Los errores transitorios conservan el próximo intento;
los definitivos se consultan y corrigen antes del reintento administrativo.

Para actualizar el repositorio, verifica primero rama y cambios locales:

```bash
git pull --ff-only origin emmanuel-noti
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

Conserva los volúmenes de RabbitMQ y PostgreSQL: no uses `docker compose down -v`
para actualizar. Haz respaldo antes de cambios de esquema. Al actualizar una
instalación con overrides Compose, conserva sus mismos argumentos `-f` y su `.env`.

## Seguridad y alcance de las pruebas

No subir `.env`, contraseñas SMTP, claves internas ni archivos PEM. No colocar `X-Internal-Key` en el frontend. Restringir el puerto del microservicio a emisores autorizados y usar TLS cuando la red lo requiera; una clave no cifra HTTP. El servicio confía en el rol transmitido por el BFF junto con la credencial interna, no autentica usuarios Azure por sí mismo.

Un build correcto o un ACK del broker no prueba recepción de correo en la
bandeja del cliente. Comprueba por separado persistencia, consumidores, permisos
y envío con una cuenta de demostración autorizada. Los archivos de pruebas no
forman parte de esta versión. No incluyas contenido de mensajes, destinatarios
reales, tokens o secretos al publicar evidencias y documentación.

## Avisos agrupados por solicitud

`GET /api/notifications/solicitudes` devuelve `orderId`, `avisos` y
`ultimaActividad`, agrupando en la base antes de paginar. Admite `orderId`, `page`
y `size`. Cliente solo obtiene los grupos de su correo autenticado; Operador
puede consultar el seguimiento de correos. Se conserva el listado de avisos
`GET /api/notifications?orderId=...` para desplegar el historial de cada grupo.

## Presentación EP3

Consulta [la demostración de los cinco requisitos](EP3_DEMOSTRACION.md).
`DeclaracionTopologiaRabbit` declara seis beans Queue, tres Exchange y nueve
Binding. Los argumentos coinciden con las colas quorum existentes: no se borran,
vacían ni recrean para aplicar la configuración. El administrador tiene una
operación DELETE de bindings para recursos de demostración protegidos.
