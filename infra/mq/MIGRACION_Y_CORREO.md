# RabbitMQ y correo real - cambio del 21 de octubre de 2026

## Flujo

Frontend → API Gateway → BFF → Órdenes.
Órdenes guarda su orden y outbox en una transacción.

- Catálogo: HTTP interno para stock.
- Reportería y Auditoría: HTTP interno desde el outbox, con reintentos independientes.
- Notificaciones: comandos persistentes por RabbitMQ, ACK después de guardar.
- Correo: texto UTF-8 con el detalle de la solicitud, sin generar ni adjuntar PDF,
  enviado con el proveedor SMTP autorizado de Talleres360.

El cliente solicita acceso institucional, Admin habilita su identidad y rol en
Entra y después envía un carrito sin pago online. Operador acepta o rechaza con
motivo; aceptar solo se completa después de confirmar stock. Los tres avisos son
aceptación, lista para retirar/entregar y rechazo. El comprobante es únicamente
texto del correo. No se descuenta stock al enviar el carrito.

Kafka, ZooKeeper y su administrador no forman parte de esta nueva versión local.
Este cambio no implica que se haya apagado o eliminado su EC2 en AWS.

## Desplegar sin perder datos

1. Respaldar las bases y mantener los volúmenes. No ejecutar `down -v`, purgas ni
   reiniciar secuencias. Suspender temporalmente escrituras durante el corte.
2. Antes de detener el Kafka antiguo, comprobar que Reportería y Auditoría
   procesaron todos los eventos y revisar sus DLT. Los marcadores del outbox
   anterior confirman publicación al broker, no necesariamente consumo final.
   Si quedan mensajes o no se puede demostrar la recepción, conservar el broker
   y sus volúmenes y reconciliar los eventId antes de retirarlo.
3. Actualizar Reportería y Auditoría en sus ramas. Mantienen `/internal/events`.
   Sus `.env` ya no necesitan las variables `KAFKA_*`.
4. Actualizar Notificaciones. Flyway V3 añade una columna sin borrar avisos.
5. Actualizar administrador Rabbit. Inicializar con el BFF como Admin y revisar
   las seis colas, nueve bindings y tres políticas. La inicialización retira
   `invoice.#` después de asegurar `invoice.*`; no purga mensajes.
6. En Órdenes configurar `MESSAGING_TRANSPORT=rabbit`, direcciones y credenciales
   de RabbitMQ y URL privadas `REPORT_URL` y `AUDIT_URL`. El valor antiguo `broker`
   se rechaza al iniciar para evitar perder silenciosamente notificaciones.
7. Actualizar Órdenes y BFF. Quitar `KAFKA_ADMIN_URL`. No cambian la URL de Gateway
   ni la de frontend; las rutas de datos de los cinco servicios se mantienen.
8. Probar una solicitud nueva hasta entrega: actor y evento en Auditoría, venta
   en Reportería, stock correcto y tickets. Probar SMTP con una
   dirección de prueba autorizada; comprobar recepción del mensaje de texto.
9. Solo después retirar la EC2/infraestructura Kafka mediante una operación AWS
   explícita. Esta guía no elimina instancias ni sus discos.

Las colas quorum existentes no se convierten a Classic. Sus argumentos DLX
anteriores prevalecen sobre políticas: el administrador lo avisa y conserva
mensajes. No se afirma una migración de argumentos completada solo por inicializar.

## Dos cuentas diferentes

- Destinatario: correo real del cliente autenticado, por ejemplo `@duocuc.cl`.
  Crear una identidad Entra no crea necesariamente un buzón de correo.
- Remitente: cuenta de servicio que Talleres360 está autorizada a usar para enviar.
  El login del cliente no otorga esa autorización ni proporciona credenciales SMTP.

En la raíz de Notificaciones, archivo privado `.env`, completar con el proveedor
del remitente. No usar contraseñas de clientes y no publicar este archivo:

```dotenv
SMTP_ENABLED=false
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=
SMTP_AUTH=true
SMTP_STARTTLS=true
RABBIT_ENABLED=true
RABBIT_DLQ_AUTO_STARTUP=false
```

Activar `SMTP_ENABLED=true` solo con los campos completos y un proveedor que
admita este método. Si la institución solo permite OAuth2 o Microsoft Graph,
se necesita ese adaptador: no se soluciona pegando una contraseña normal.
No se deduce el servidor SMTP por el sufijo `@duocuc.cl`.

STARTTLS es obligatorio por defecto y se valida el certificado del servidor.
Sin proveedor, los correos quedan pendientes: no se simula que fueron enviados.
Que SMTP acepte el mensaje no garantiza bandeja de entrada; revisar spam/rebotes.
El comprobante es de servicio, no una factura o boleta tributaria.

## Registro y aprobación

El flujo solicitado es registro → aprobación y rol Cliente en Entra ID → login
→ solicitud → correo al destinatario asociado. No debe crearse un registro local
de contraseñas ni asignar roles desde React. Configurar autoservicio/invitaciones
y la aprobación en el tenant correspondiente es un trabajo de identidad separado.
La aprobación en Entra y una cuenta remitente SMTP no se configuran con este cambio.

## Verificación de la guía RabbitMQ

`cmd.direct`: email.send, workshop.ticket, invoice.gen.
`cmd.topic`: email.#, workshop.#, invoice.*.
`cmd.dead.dlx`: mismas claves directas hacia sus DLQ.

Para observar rechazos manuales detener temporalmente consumidores principales,
publicar un mensaje de prueba y usar Reject sin requeue. Con los consumidores
activos, un mensaje válido puede procesarse antes de verlo en Management.
Automatic ack retira el mensaje: no usarlo para inspeccionar sin consumir.

Referencias: [políticas DLX de RabbitMQ](https://www.rabbitmq.com/docs/dlx),
[registro autoservicio de Entra ID](https://learn.microsoft.com/en-us/entra/external-id/self-service-sign-up-overview).

## Verificación local de este cambio

Compilaron Órdenes, Reportería, Auditoría, Notificaciones, BFF y administrador
RabbitMQ. Los seis Compose validaron su estructura con valores ficticios en
memoria, sin iniciar contenedores. Las pruebas temporales comprobaron reintento
HTTP, independencia de confirmaciones, NACK/ACK del publicador simulado, ausencia
de duplicación HTTP/Rabbit, seis colas/tres políticas/nueve enlaces, espera del
comprobante sin gastar intentos SMTP y correo MIME con PDF adjunto válido.

No se enviaron correos reales, no se probó un broker real en esta intervención,
no se cambió Azure y no se desplegó ni eliminó ninguna EC2. Varios `.env` locales
no tienen todas las URL/credenciales necesarias: la validación estructural no
significa que esos archivos privados estén listos para ejecutar el stack.
