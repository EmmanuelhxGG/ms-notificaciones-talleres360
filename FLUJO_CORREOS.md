# Correos automáticos de solicitudes

El operador cambia el estado. Órdenes confirma el stock cuando corresponde y
guarda el aviso en su outbox. El publicador envía JSON persistente a RabbitMQ.
Notificaciones guarda una bandeja durable antes de confirmar el consumo y luego
intenta enviar el correo mediante el proveedor configurado. El frontend nunca
envía correo ni proporciona credenciales SMTP.

Los tres tipos de aviso son aceptación, lista para retirar y rechazo/cancelación
con motivo. No se envía otro correo al comenzar la preparación o confirmar la
entrega. Cada solicitud recorre los estados que correspondan; no recibe siempre
los tres avisos. El mensaje incluye productos, cantidades, monto y el operador
que originó la acción. El remitente técnico debe estar autorizado por el proveedor.

El contrato usa `pedidoId`, `email`, `asunto` y `mensaje`, más `commandId`, `tipo`
y `occurredAt`. Estos metadatos permiten detectar reentregas sin duplicar registros.
Se aceptan los alias anteriores `orderId` y `destinatario` para los mensajes ya
pendientes. Los ejemplos de cuatro campos de la guía son esquemáticos: para
publicar manualmente en esta aplicación también se requieren los metadatos.

Los correos anteriores no enviados de la misma solicitud impiden adelantar uno
posterior. Una caída temporal conserva el reintento; falta de configuración
conserva el estado pendiente. El consumo de RabbitMQ no acredita recepción en el
buzón: `ENVIADA` significa que el proveedor SMTP aceptó el envío. SMTP no ofrece
una transacción conjunta con la base; tras una caída justo después de enviar y
antes de confirmar el estado, un reintento podría repetir el correo.

Operador consulta los correos y sus estados reales en Notificaciones; Cliente
consulta solo sus avisos. Los tickets históricos se conservan en la base y no se
presentan como correos enviados. No se generan PDF ni adjuntos.

La configuración privada de la EC2 incluye `SMTP_ENABLED`, `SMTP_HOST`,
`SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` y las opciones TLS/auth
compatibles con el proveedor. No se activará el envío sin una cuenta real
autorizada. Tener una identidad en Entra ID no configura un proveedor de correo.
