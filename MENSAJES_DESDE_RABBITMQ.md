# Publicar un mensaje personalizado desde RabbitMQ

## JSON: solo tres campos

```json
{
  "email": "tu-cuenta@duocuc.cl",
  "asunto": "Mensaje de prueba",
  "mensaje": "Este aviso se publicó directamente desde RabbitMQ."
}
```

Reemplaza `email` por el correo de la cuenta destinataria. Puede ser una cuenta
Cliente, Operador o Admin habilitada en la plataforma. No necesitas `pedidoId`,
tipo ni fecha. El servicio registra un **mensaje general**, sin inventar una orden.

## Dónde pegarlo

1. Abre RabbitMQ Management de la instalación mediante tu acceso privado habitual.
2. Entra a **Exchanges**, abre **cmd.direct** y despliega **Publish message**.
3. Routing key: **email.send**.
4. En las propiedades coloca `content_type` = `application/json` y
   `delivery_mode` = `2` (Persistent).
5. Payload encoding: **String**. Pega el JSON completo, sin envolverlo en comillas
   ni convertirlo en un texto con barras invertidas.
6. Pulsa **Publish message** una vez. Debe indicar que se enrutó correctamente.
7. Inicia sesión en la aplicación con la cuenta del campo `email`, abre
   **Notificaciones → Mensajes generales → Ver mensaje**. Se consulta cada cinco
   segundos; también puedes usar Actualizar / limpiar.

También se puede publicar desde la cola **q.cmd.email**, con exchange
predeterminado vacío y routing key **q.cmd.email**. No usar ambas rutas para
intentar crear mensajes distintos con el mismo contenido.

## Evitar duplicados

Sin `message_id`, repetir exactamente email, asunto y mensaje representa la misma
tarea y no crea otro aviso. Para emitir intencionalmente otro mensaje idéntico,
agrega en **Properties**, fuera del JSON, un `message_id` nuevo, por ejemplo
`presentacion-02`. Conserva ese mismo valor al reintentar esa publicación. El
cuerpo JSON sigue teniendo únicamente sus tres campos.

Reutilizar `message_id` con otro contenido se rechaza: no se sobrescribe un aviso.
Este control deduplica el comando persistido; no convierte SMTP en entrega
exactamente una vez ante cualquier caída.

## Recorrido del mensaje

```text
JSON en Management
  -> cmd.direct / email.send
  -> q.cmd.email
  -> ConsumidorRabbit
  -> validación y persistencia en PostgreSQL
  -> ACK manual
  -> bandeja personal del destinatario, a través del BFF
  -> proceso SMTP, si está habilitado
  -> Mailpit del laboratorio o proveedor autorizado configurado
```

La bandeja web puede mostrar PENDIENTE antes de que SMTP procese el correo.
ENVIADA indica aceptación de SMTP. Mailpit captura los correos de laboratorio;
no los entrega por Internet a una bandeja externa. El mensaje aparece en la
aplicación por su destinatario, no por un rol escrito dentro del JSON.

Cada rol tiene bandeja personal. Operador dispone además de **Seguimiento de
envíos**, separado de sus mensajes propios. Admin no obtiene por eso permiso para
consultar mensajes personales ajenos. El BFF deriva el correo del JWT y el servicio
filtra por ese destinatario.

## Si se consume y no aparece

- Comprueba que `email` coincide con el correo autenticado de la cuenta abierta.
- Busca en Mensajes generales, sin filtro de número de solicitud.
- Comprueba si pasó a DLQ por JSON inválido, email inválido, asunto vacío o cola
  incompatible. Las DLQ se archivan en `mensajes_fallidos`; que estén vacías no
  descarta un error anterior.
- Comprueba PENDIENTE/REINTENTO/FALLIDA si esperabas además un correo SMTP.
- Comprueba que publicador y consumidor usan el mismo broker y vhost.

## Actualizar una base existente

Los mensajes generales requieren `notificaciones.order_id` nullable. En una base
PostgreSQL existente hay que respaldar y ejecutar
`src/main/resources/db/manual/postgresql_notificaciones_generales.sql` antes de
activar esta versión. No se eliminan registros, no se renumeran solicitudes y no
se borran volúmenes. El despliegue debe conservar sus overrides Mailpit/runtime.

## Diferencia con una solicitud de compra

El cliente arma carrito y envía; Órdenes comprueba cantidades/precios sin descontar
todavía stock. Operador acepta; Catálogo confirma la asignación y entonces se
registra ACEPTADA y su aviso en el outbox. El publicador envía a Rabbit; el
consumidor persiste y confirma; el proceso SMTP trabaja después. Preparar y marcar
lista para retiro genera el aviso correspondiente; rechazar/cancelar exige motivo
y genera ese aviso. Son tres tipos de aviso, no tres envíos obligatorios por pedido.
Los automáticos se agrupan por solicitud; los JSON de tres campos van a generales.
