# RabbitMQ de Talleres360

Despliegue independiente para `ec2-talleres360-mq`. No utiliza `ec2-apps`.
Incluye dos nodos RabbitMQ y un administrador Spring Boot con capas controller,
service, repository y modelos validados. Requiere Docker Engine, Buildx y Compose.

Documentación actualizada el **22 de octubre de 2026**. La topología se adapta a
Talleres360, un único taller. RabbitMQ procesa tareas de notificación; stock,
reportes y auditoría usan el outbox HTTP de Órdenes. Kafka no forma parte de esta
versión. La imagen de RabbitMQ se fija en `compose.yml`.

## Configuración

1. Copiar `.env.example` como `.env` en esta carpeta.
2. Completar usuario, contraseña RabbitMQ, cookie compartida e `INTERNAL_API_KEY`.
3. Ejecutar `docker compose up -d --build`.
4. Desde una conexión autorizada, inicializar con
   `POST /api/messaging/rabbit/initialize`, encabezados `X-Internal-Key` y
   `X-Actor-Role: Admin`. En la aplicación pública el BFF verifica el JWT Admin.

El archivo `.env` es privado y no se versiona. Para discos pequeños existe
`administrador/Dockerfile.runtime`: compilar el administrador con Java 21/Maven,
copiar el JAR como `app.jar` a un contexto de construcción separado junto con ese
Dockerfile y construir la imagen sin descargar Maven en la EC2.

El archivo privado se crea en **`infra/mq/.env`**, junto al Compose de esta carpeta:

```dotenv
RABBIT_USERNAME=<USUARIO_RABBIT_AUTORIZADO>
RABBIT_PASSWORD=<CONTRASENA_PRIVADA_RABBIT>
RABBIT_COOKIE=<COOKIE_ERLANG_PRIVADA_COMPARTIDA>
INTERNAL_API_KEY=<CLAVE_INTERNA_COMPARTIDA>
```

Reemplaza los marcadores antes de iniciar. La cookie se comparte solo entre los
nodos Rabbit; no se copia al frontend. Usuario, contraseña y vhost deben coincidir
con Órdenes y Notificaciones. En un broker ya inicializado, cambiar `.env` no
equivale a rotar las credenciales guardadas: no borres sus volúmenes para hacerlo.

Comprueba la instalación desde esta carpeta:

```bash
docker buildx version
docker compose version
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

## Topología

| Cola principal | Routing key directo | Cola de errores |
|---|---|---|
| `q.cmd.email` | `email.send` | `q.cmd.email.dlq` |
| `q.cmd.workshop` | `workshop.ticket` | `q.cmd.workshop.dlq` |
| `q.cmd.invoice` | `invoice.gen` | `q.cmd.invoice.dlq` |

`cmd.direct` enruta por coincidencia exacta; `cmd.topic` permite `email.#`,
`workshop.#` e `invoice.*`; `cmd.dead.dlx` enruta los rechazos. Así, `invoice.gen`
coincide, pero `invoice.gen.pdf` no. `kitchen` del ejemplo se adapta a `workshop`.
Se conservan las seis colas quorum durables y la versión RabbitMQ actual, en vez
de volver a Classic/RabbitMQ 3.13 del laboratorio. No se baja una versión ni se
reemplaza una cola existente destruyendo mensajes.

El inicializador crea tres políticas `policy.email.dlx`, `policy.workshop.dlx`
y `policy.invoice.dlx`, con patrones exactos `^q\.cmd\.email$`,
`^q\.cmd\.workshop$` y `^q\.cmd\.invoice$`. Definen `dead-letter-exchange`,
`dead-letter-routing-key`, `dead-letter-strategy=at-least-once` y
`overflow=reject-publish`. No afectan a las DLQ. `GET /policies` permite revisarlas.

Una cola de la versión anterior puede tener argumentos `x-dead-letter-*`:
prevalecen sobre las políticas. El inicializador comprueba compatibilidad y avisa,
pero no la borra ni purga. Para dejarla únicamente con políticas hay que detener
productores, terminar entregas, respaldar y recrear la cola vacía explícitamente.
La API protege las colas de negocio y no hace ese cambio destructivo por sí sola.
Órdenes publica mensajes persistentes con confirmación y detección de mensajes
sin ruta. Solo marca el outbox después de recibir confirmación.

Notificaciones consume las tres principales y confirma ACK después de guardar la
tarea idempotente. Un comando inválido recibe NACK sin reencolar. Los consumidores
DLQ se mantienen detenidos por defecto (`RABBIT_DLQ_AUTO_STARTUP=false`) para
observar Ready y `x-death` como en la guía. Al activar esa variable guardan el
fallo en base de datos y confirman: en ese modo la DLQ puede volver a quedar vacía.
SMTP se procesa por separado: sin un servidor configurado no se afirma que el
correo fue enviado. El comprobante nuevo es texto dentro del correo, sin PDF ni
adjuntos y no es un documento tributario. `q.cmd.invoice` se mantiene por la
topología de la guía y por compatibilidad histórica, pero las compras nuevas
no publican comandos para generar documentos.

## Flujo y recuperación

```text
Operador cambia una solicitud
    → Órdenes guarda evento y comandos en su outbox
    → publicación persistente con confirmación RabbitMQ
    → cola durable
    → Notificaciones guarda la tarea en PostgreSQL y confirma ACK
    → worker SMTP envía texto o reintenta; el ticket queda consultable
```

Los correos corresponden a aceptación, solicitud lista para retiro/entrega y
rechazo con motivo. Si cae el broker, el productor conserva su outbox; si cae el
consumidor, conserva la cola; después del ACK la recuperación depende de la tarea
persistida en Notificaciones. Los duplicados de entrega se deduplican por UUID.
Esto no garantiza exactamente un correo SMTP en todos los fallos posibles.

## Administración y acceso

El administrador ofrece GET `queues`, `exchanges`, `nodes`, `bindings`, `policies`; POST
`queues`, `exchanges`, `bindings`, `initialize`; DELETE `queues/{nombre}` y
`exchanges/{nombre}`, bajo `/api/messaging/rabbit`. La eliminación se restringe a
recursos `q.demo.*` y `cmd.demo.*` para proteger las colas de negocio.
Antes de eliminar una cola demo se comprueba que no tenga mensajes ni consumidores.
RabbitMQ no admite las condiciones `if-empty`/`if-unused` para quorum: en ese tipo,
la comprobación previa no es atómica. No publicar ni consumir en una cola demo
mientras se elimina. Las colas principales y DLQ no se pueden borrar mediante esta API.

- 5672 y 5673: permitir únicamente los grupos de seguridad de Órdenes y Notificaciones.
- 8086: permitir únicamente el grupo de seguridad del BFF.
- 22: permitir únicamente la IP pública del administrador, con máscara `/32`.
- Management UI: escucha en `127.0.0.1:15672`; acceder mediante túnel SSH.

La aplicación web consume el administrador a través de BFF con rol Admin. Configura
en BFF `RABBIT_ADMIN_URL=http://<IP_PRIVADA_EC2_RABBIT>:8086`. En Órdenes y
Notificaciones configura
`RABBIT_ADDRESSES=<IP_PRIVADA_EC2_RABBIT>:5672,<IP_PRIVADA_EC2_RABBIT>:5673`.
Son destinos privados: no se agregan al `.env.local` del frontend.

Ejemplo de túnel desde una terminal autorizada, sustituyendo ambos marcadores:

```bash
ssh -i "<RUTA_A_TU_LLAVE_PEM>" -N -L 15672:127.0.0.1:15672 ec2-user@<DNS_PUBLICO_EC2_RABBIT>
```

Abre `http://127.0.0.1:15672` e ingresa las credenciales privadas del broker.
No publiques el puerto Management a Internet. Mantén las llaves PEM fuera del
repositorio y no copies su contenido, contraseñas, cookies o tokens a este README.

## Límites del laboratorio

Ambos nodos están en la misma EC2: no hay tolerancia a la caída de esa máquina.
Un quorum de dos miembros requiere los dos disponibles; no se presenta como alta
disponibilidad productiva. La alternativa productiva son tres nodos en máquinas
distintas. El disco es de 8 GB, con volúmenes persistentes y logs rotados; no usar
`docker compose down -v` si se deben conservar mensajes.
