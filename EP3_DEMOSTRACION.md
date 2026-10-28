# Demostración EP3: RabbitMQ

## 1. Configuración central y beans

En cada servicio, abrir `config/TopologiaRabbit.java`. Es el contrato central de
colas, exchanges, routing keys y patrones, configurable con `app.rabbit.topologia`.
No se copian nombres de infraestructura dentro del publicador ni de los listeners.
Órdenes y Notificaciones tienen el mismo contrato porque son repositorios
independientes; cualquier cambio de nombres debe aplicarse coordinadamente.

Abrir `config/DeclaracionTopologiaRabbit.java`: contiene seis beans Queue, tres
Exchange y nueve Binding. Las colas son durables, quorum y conservan los argumentos
DLX existentes. RabbitAdmin declara los beans sin eliminar colas ni mensajes.

## 2. ACK, NACK y reintentos

- `messaging/ConsumidorRabbit`: datos inválidos → NACK sin reencolar → DLQ.
- Una tarea válida se guarda transaccionalmente; solo después se hace ACK manual.
- `config/ReintentosRabbit`: tres intentos con espera exponencial para fallas de
  persistencia. No se repiten datos inválidos. Agotados los intentos → DLQ.
- `service/RegistroCartasMuertas`: guarda mediante `ArchivoCartasMuertas`, espera
  el commit, registra en logs y confirma ACK. Si la BD sigue caída, reencola.
- El correo se procesa desde la bandeja persistida. Su reintento no vuelve a
  publicar el comando ni depende de que siga en RabbitMQ.

## 3. API administradora

Usar la URL de API Gateway con un access token de **Admin**. Todos los ejemplos
requieren `Authorization: Bearer <TOKEN_ADMIN>` y JSON. No usar llaves internas
en el frontend ni publicar tokens en el repositorio.

Crear un exchange:

```http
POST /api/messaging/rabbit/exchanges
Content-Type: application/json

{"nombre":"cmd.demo.presentacion","tipo":"direct"}
```

Crear una cola:

```http
POST /api/messaging/rabbit/queues
Content-Type: application/json

{"nombre":"q.demo.presentacion"}
```

Crear un binding:

```http
POST /api/messaging/rabbit/bindings
Content-Type: application/json

{"exchange":"cmd.demo.presentacion","cola":"q.demo.presentacion","clave":"presentacion"}
```

Eliminar ese binding usando el mismo cuerpo:

```http
DELETE /api/messaging/rabbit/bindings
Content-Type: application/json

{"exchange":"cmd.demo.presentacion","cola":"q.demo.presentacion","clave":"presentacion"}
```

Finalmente `DELETE /api/messaging/rabbit/queues/q.demo.presentacion` y
`DELETE /api/messaging/rabbit/exchanges/cmd.demo.presentacion`.
La cola debe estar vacía y sin consumidores. Los recursos de negocio están
protegidos; el CRUD de demostración utiliza `q.demo.*` y `cmd.demo.*`.
Los listados GET de `queues`, `exchanges`, `bindings` y `nodes` permiten observar
los cambios. Nombres vacíos, tipos inválidos o claves con rutas se rechazan.

## 4. Mensaje fallido y DLQ

Desde RabbitMQ Management, publicar un JSON inválido en `cmd.direct` con routing
key `email.send`, modo persistente y un identificador de demostración único.
El mensaje no debe contener datos de clientes. Repetir para `workshop.ticket`
e `invoice.gen` si se quiere demostrar las tres rutas.

El listener principal hace NACK y RabbitMQ lo dirige a su DLQ. Con el archivo
automático activo, aparece en PostgreSQL y en los logs de Notificaciones:
`Carta muerta archivada y confirmada`, con cola e identificador. La DLQ puede
volver a cero: el mensaje quedó guardado antes del ACK, no se perdió.

Para observarlo detenido en Management, configurar temporalmente
`RABBIT_DLQ_AUTO_STARTUP=false` y recrear **solo** Notificaciones con los mismos
archivos Compose (incluido Mailpit). Después volver a `true` y comprobar su archivo.
No purgar las colas ni ejecutar `down -v`.

## 5. Flujo funcional

Crear una solicitud desde el frontend y aceptarla como Operador. Mostrar el
comando publicado, su consumo y el correo capturado por Mailpit. El buzón de
laboratorio no entrega mensajes a Internet. Los duplicados del comando se
deduplican por su identificador persistente; no se garantiza exactamente un
correo ante una caída entre la aceptación SMTP y el commit del resultado.

El clúster contiene dos nodos dentro de una EC2. No existe tolerancia a la caída
de esa máquina y las colas quorum de dos miembros requieren ambos para mayoría.
