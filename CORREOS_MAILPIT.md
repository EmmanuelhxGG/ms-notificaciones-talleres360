# Correos de laboratorio con Mailpit

Mailpit captura los correos generados por el sistema y permite leerlos en una
interfaz web. **No entrega mensajes a Outlook, Gmail ni a cuentas @duocuc.cl.**
No requiere Resend, dominio propio ni contraseñas de correo.

## Flujo

Operador acepta, marca lista para retirar o cancela una solicitud → Órdenes
publica el aviso → RabbitMQ → Notificaciones guarda y procesa el aviso → SMTP
interno de Mailpit → correo visible en la interfaz web.

El destinatario corresponde al cliente de cada solicitud. El remitente de
laboratorio es `Operador Talleres360 <operador@talleres360.test>`; esta dirección
identifica el sistema de pruebas y no es una cuenta de correo real. La acción y
el usuario operador permanecen en el contenido y en la auditoría.

## Desplegar en la EC2 de Notificaciones

Conservar el `.env` privado existente con las variables de PostgreSQL, clave
interna y conexión a RabbitMQ. No reemplazar esas direcciones por localhost:
el broker está en otra EC2. Mailpit comparte la red Docker de Notificaciones.

Desde la carpeta del repositorio:

```bash
docker compose -f compose.yml -f compose.mailpit.yml up -d --build
```

Si la instalación utiliza un archivo de imagen `compose.runtime.yml`, incluirlo
también, después de los dos archivos anteriores, para conservar esa imagen.
En los despliegues posteriores debe mantenerse `compose.mailpit.yml`; usar solo
`compose.yml` aplica nuevamente la configuración SMTP del `.env`.

No se publica el puerto SMTP 1025 de Mailpit en la EC2. La interfaz web se
publica únicamente en `127.0.0.1:8025`; no requiere abrir un Security Group.

## Abrir el buzón desde otra PC

En PowerShell, reemplazar únicamente la ruta de la llave y la IP pública actual
de **Notificaciones**. No escribir los caracteres `<` ni `>`.

En Windows también se puede abrir `Abrir-Correos.cmd` e ingresar la IP actual.
Busca la llave en `Escritorio/llaves pem cloud/ec2-talleres360-notificaciones.pem`.
Si está en otra carpeta, pasar la IP y la ruta entre comillas como argumentos.

```powershell
ssh -i "RUTA_COMPLETA_LLAVE_NOTIFICACIONES.pem" -o ExitOnForwardFailure=yes -N -L 127.0.0.1:8025:127.0.0.1:8025 ec2-user@IP_PUBLICA_NOTIFICACIONES
```

Mantener esa ventana abierta y visitar **http://localhost:8025**. No se necesita
Docker en la PC. Si el puerto local está ocupado, cambiar solo el primer 8025
por 8026 y abrir http://localhost:8026. Cerrar SSH cierra el acceso al buzón,
pero no detiene la captura de correos en AWS.

## Comprobar el flujo

1. El cliente envía una solicitud desde su carrito.
2. El operador la acepta: aparece un correo con productos y monto.
3. El operador la prepara y marca lista para retirar: aparece el segundo aviso.
4. En otra solicitud, cancelar con motivo genera el aviso de rechazo/cancelación.

La creación y la preparación no generan correos adicionales. Los avisos
pendientes anteriores también se procesan al habilitar SMTP.

Si Notificaciones se detiene, RabbitMQ conserva los comandos pendientes según
su configuración durable. Si Mailpit se detiene, Notificaciones conserva y
reintenta los envíos en su base de datos. No se debe confirmar un mensaje como
enviado antes de que el SMTP lo acepte. Los identificadores persistentes evitan
reprocesamientos normales; no se garantiza exactamente una vez si el proceso
cae después de que SMTP acepta el correo y antes de guardar el resultado.

## Persistencia y alcance

- El volumen `datos_mailpit` conserva el buzón tras reiniciar el contenedor o EC2.
- No ejecutar `docker compose down -v`: elimina los volúmenes y sus datos.
- Para limitar el uso del disco del laboratorio, Mailpit conserva hasta 1000
  correos y elimina automáticamente los más antiguos al superar ese límite.
- `ENVIADA` indica aceptación por el SMTP configurado. Con este perfil significa
  **capturado por Mailpit**, no entregado a una bandeja de Internet.
- El buzón es compartido y solo para demostración del encargado mediante SSH;
  no debe exponerse públicamente ni utilizarse como bandeja privada del cliente.
