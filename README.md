# Talleres360 — Microservicio de notificaciones

Servicio independiente que recibe comandos de notificación, genera tickets de taller y envía correos mediante SMTP. Mantiene su propia base de datos y no modifica órdenes ni stock.

La infraestructura RabbitMQ independiente está en [infra/mq](infra/mq/README.md).
Con `RABBIT_ENABLED=true` consume tres colas durables: correo, ticket de taller y
comprobante PDF. Usa ACK/NACK manual, deduplicación por commandId y tres DLQ cuyos
fallos se conservan en la base de datos. El comprobante de servicio no reemplaza
una boleta/factura del SII y se descarga mediante
`GET /api/notifications/{id}/receipt`, previa autorización del BFF y del servicio.

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
5. Los fallos SMTP generan reintentos con espera creciente. Tras cinco intentos el estado es `FALLIDA`. Un administrador puede volver a intentarlo.

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

Las operaciones de negocio requieren `X-Internal-Key`. La consulta requiere `X-Actor-Role`: `Operador` consulta solo tickets; `Cliente` consulta únicamente correos cuyo destinatario coincide con `X-Customer-Email`, obtenido del token por el BFF. Sin identidad del cliente la consulta se rechaza. Admin no consulta notificaciones por esta API. El reintento Admin es una operación interna de mantenimiento y no tiene ruta pública en el BFF. El tamaño máximo de página es 100 y la numeración comienza en 0.

La respuesta paginada tiene `content`, `page`, `size`, `totalElements` y `totalPages`. No se exponen entidades JPA directamente.

Ejemplo de comando HTTP o mensaje RabbitMQ:

```json
{
  "commandId": "6aff7699-5367-42df-9fbc-232de2627c80",
  "orderId": 15,
  "tipo": "CORREO",
  "destinatario": "cliente@ejemplo.cl",
  "asunto": "Tu orden fue aceptada",
  "mensaje": "La orden 15 fue aceptada por el taller.",
  "occurredAt": "2026-10-08T15:30:00Z"
}
```

Para un ticket usar `"tipo": "TICKET_TALLER"`; el destinatario puede ser `null`. El ticket se relaciona con la orden por `orderId`, no es una orden adicional. El mismo `commandId` se conserva en todos los reintentos del emisor.

## Ejecutar y probar

Requisitos: JDK 21 y conexión para descargar dependencias en la primera ejecución. El repositorio incluye Maven Wrapper.

En PowerShell, desde la carpeta del proyecto:

```powershell
$env:INTERNAL_API_KEY='una-clave-interna-larga-para-pruebas-locales'
.\mvnw.cmd '-DskipTests' package
.\mvnw.cmd spring-boot:run
```

En Linux/macOS, usar `./mvnw` en lugar de `.\mvnw.cmd` y exportar la variable. El perfil `local` usa H2: los datos se pierden al detener la aplicación. Maven no carga `.env` automáticamente; ese archivo lo utiliza Compose.

## Docker Compose independiente

1. Copiar `.env.example` como `.env`.
2. Completar `DB_PASSWORD` e `INTERNAL_API_KEY` con valores propios; mantener `DB_USERNAME=talleres360` o elegir otro usuario.
3. Ejecutar:

```sh
docker compose up -d --build
```

La imagen se construye desde este repositorio como `talleres360/ms-notificaciones:local`. PostgreSQL no publica su puerto al host. La aplicación espera a que la base esté disponible y ambos contenedores usan `restart: unless-stopped`; esto requiere que el motor Docker arranque con el equipo. El volumen `talleres360-notificaciones_datos_postgres` conserva los datos.

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
| `RABBIT_ENABLED` | Activar el consumidor. Desactivado por defecto. |
| `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USERNAME`, `RABBIT_PASSWORD`, `RABBIT_VHOST` | Conexión al broker. |
| `RABBIT_EMAIL_QUEUE`, `RABBIT_WORKSHOP_QUEUE` | Colas, por defecto `q.cmd.email` y `q.cmd.workshop`. |
| `NOTIFICATIONS_POLL_MS` | Intervalo del proceso periódico, 3000 ms por defecto al ejecutar Java. |
| `DDL_AUTO` | Gestión de esquema; `update` para este laboratorio. |

Los chequeos SMTP/Rabbit del endpoint de salud se activan con sus respectivas integraciones. Las opciones avanzadas de Spring pueden configurarse mediante variables de entorno adicionales.

El perfil Oracle usa `DB_URL=jdbc:oracle:thin:@//HOST:1521/SERVICIO`, usuario y contraseña propios. No cambia el Compose PostgreSQL ni convierte datos existentes. La validación automatizada de este repositorio utiliza H2; una base Oracle real requiere su prueba de conexión y esquema.

## RabbitMQ y conexión con el resto del sistema

El consumidor procesa JSON desde las dos colas configuradas y confirma la recepción después de persistir el comando. Los mensajes malformados o mayores de 64 KiB se rechazan. Los errores de procesamiento se reintentan tres veces; el broker necesita una DLQ y un enlace de mensajes rechazados para conservar los fallidos. El servicio no crea colas, exchanges, bindings ni clústeres.

La entrada HTTP interna permite probarlo sin RabbitMQ. No sustituye la integración de mensajería exigida por el caso. El servicio no inventa destinatarios ni recibe tokens Azure del navegador: el BFF valida al usuario y debe colocar los encabezados internos confiables.

API Gateway sigue apuntando al BFF, no directamente a este servicio. Órdenes prepara comandos durables y los envía por HTTP interno conservando el UUID en cada reintento. El BFF expone `GET /api/notifications`, con la identidad derivada del JWT. Configurar `NOTIFICATIONS_URL=http://IP_PRIVADA:8084` en Órdenes y BFF, junto con la misma clave interna de este servicio. El frontend ofrece tickets al Operador y avisos propios al Cliente. No se han creado ni modificado recursos AWS como parte de esta integración.

## Seguridad y alcance de las pruebas

No subir `.env`, contraseñas SMTP, claves internas ni archivos PEM. No colocar `X-Internal-Key` en el frontend. Restringir el puerto del microservicio a emisores autorizados y usar TLS cuando la red lo requiera; una clave no cifra HTTP. El servicio confía en el rol transmitido por el BFF junto con la credencial interna, no autentica usuarios Azure por sí mismo.

La revisión local del 8 de octubre verificó validaciones, deduplicación, permisos, tickets, paginación y reintentos con SMTP simulado. No envió correos reales ni verificó un broker o EC2. Los archivos de pruebas no forman parte de esta versión; el empaquetado no sustituye la validación del despliegue.
