# RabbitMQ de Talleres360

Despliegue independiente para `ec2-talleres360-mq`. No utiliza `ec2-apps`.
Incluye dos nodos RabbitMQ y un administrador Spring Boot con capas controller,
service, repository y modelos validados. Requiere Docker Engine, Buildx y Compose.

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
correo fue enviado. Las entregas generan un comprobante PDF de servicio, no un
documento tributario.

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

## Límites del laboratorio

Ambos nodos están en la misma EC2: no hay tolerancia a la caída de esa máquina.
Un quorum de dos miembros requiere los dos disponibles; no se presenta como alta
disponibilidad productiva. La alternativa productiva son tres nodos en máquinas
distintas. El disco es de 8 GB, con volúmenes persistentes y logs rotados; no usar
`docker compose down -v` si se deben conservar mensajes.
