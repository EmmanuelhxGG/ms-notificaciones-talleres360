@echo off
setlocal
set "IP=%~1"
if not defined IP set /p "IP=IP publica actual de la EC2 de Notificaciones: "
if not defined IP exit /b 1
set "LLAVE=%~2"
if not defined LLAVE set "LLAVE=%USERPROFILE%\Desktop\llaves pem cloud\ec2-talleres360-notificaciones.pem"
if not exist "%LLAVE%" (
  echo No se encontro la llave. Ejecuta este archivo con la IP y la ruta completa de tu llave como argumentos.
  pause
  exit /b 1
)
echo Conectando al buzon de laboratorio de AWS.
echo Cuando se establezca la conexion, abre http://localhost:8025
echo Mantener esta ventana abierta. Ctrl+C cierra el acceso sin detener Mailpit.
ssh -i "%LLAVE%" -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -N -L 127.0.0.1:8025:127.0.0.1:8025 "ec2-user@%IP%"
if errorlevel 1 pause
